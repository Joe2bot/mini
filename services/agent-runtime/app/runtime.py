from __future__ import annotations

import asyncio
import os
import time
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any
from uuid import UUID, uuid4

from app.llm import LLMAdapter
from app.schemas import RuntimeEvent, RuntimeStartRequest


@dataclass
class RunSession:
    request: RuntimeStartRequest
    events: list[RuntimeEvent] = field(default_factory=list)
    condition: asyncio.Condition = field(default_factory=asyncio.Condition)
    terminal: bool = False

    async def append(self, event_type: str, data: dict[str, Any]) -> RuntimeEvent:
        async with self.condition:
            event = RuntimeEvent.create(len(self.events) + 1, event_type, self.request.run_id, self.request.trace_id, data)
            self.events.append(event)
            if event_type in {"run.completed", "run.failed"}:
                self.terminal = True
            self.condition.notify_all()
            return event


class RunManager:
    def __init__(self, adapter: LLMAdapter):
        self.adapter = adapter
        self.sessions: dict[UUID, RunSession] = {}

    async def start(self, request: RuntimeStartRequest) -> None:
        if request.run_id in self.sessions:
            return
        session = RunSession(request=request)
        self.sessions[request.run_id] = session
        asyncio.create_task(self._execute(session))

    async def _execute(self, session: RunSession) -> None:
        request = session.request
        span_id = str(uuid4())
        started = time.perf_counter()
        try:
            await session.append("run.started", {"status": "running"})
            agent = request.configuration_snapshot["agent"]
            await session.append("trace.span.started", {
                "span_id": span_id,
                "parent_span_id": None,
                "span_type": "LLM",
                "name": "agent.llm",
                "input": {"system_prompt": agent["system_prompt"], "user_input": request.input},
            })
            output: list[str] = []
            async def generate() -> None:
                configured_model = os.getenv("LLM_MODEL", agent["model_name"])
                async for delta in self.adapter.stream(agent["system_prompt"], request.input, configured_model, agent.get("temperature"), agent.get("max_tokens")):
                    output.append(delta)
                    await session.append("llm.delta", {"delta": delta})
            await asyncio.wait_for(generate(), timeout=request.limits.total_execution_timeout_seconds)
            answer = "".join(output)
            await session.append("trace.span.completed", {"span_id": span_id, "output": {"answer": answer}, "latency_ms": round((time.perf_counter() - started) * 1000)})
            await session.append("run.completed", {"status": "completed", "output": answer})
        except Exception as error:
            await session.append("trace.span.failed", {"span_id": span_id, "error": {"code": "LLM_REQUEST_FAILED", "message": "LLM execution failed"}, "latency_ms": round((time.perf_counter() - started) * 1000)})
            await session.append("run.failed", {"status": "failed", "code": "LLM_REQUEST_FAILED", "message": "LLM execution failed"})

    async def stream_events(self, run_id: UUID, last_event_id: int = 0) -> AsyncIterator[RuntimeEvent]:
        session = self.sessions[run_id]
        cursor = last_event_id
        while True:
            async with session.condition:
                pending = [event for event in session.events if event.event_id > cursor]
                if not pending and not session.terminal:
                    await session.condition.wait()
                    continue
                terminal = session.terminal
            for event in pending:
                cursor = event.event_id
                yield event
            if terminal and not pending:
                return
