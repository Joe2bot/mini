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
from app.tools import ToolDefinition, ToolRegistry


class RunFailure(Exception):
    def __init__(self, code: str, message: str): self.code=code; self.message=message; super().__init__(message)


@dataclass
class RunSession:
    request: RuntimeStartRequest
    events: list[RuntimeEvent] = field(default_factory=list)
    condition: asyncio.Condition = field(default_factory=asyncio.Condition)
    terminal: bool = False
    async def append(self, event_type: str, data: dict[str, Any]) -> RuntimeEvent:
        async with self.condition:
            event=RuntimeEvent.create(len(self.events)+1,event_type,self.request.run_id,self.request.trace_id,data)
            self.events.append(event)
            if event_type in {"run.completed","run.failed"}: self.terminal=True
            self.condition.notify_all()
            return event


class RunManager:
    def __init__(self, adapter: LLMAdapter): self.adapter=adapter; self.sessions: dict[UUID,RunSession]={}
    async def start(self, request: RuntimeStartRequest) -> None:
        if request.run_id in self.sessions: return
        session=RunSession(request=request); self.sessions[request.run_id]=session; asyncio.create_task(self._execute(session))

    async def _execute(self, session: RunSession) -> None:
        try:
            await asyncio.wait_for(self._agent_loop(session), timeout=session.request.limits.total_execution_timeout_seconds)
        except asyncio.TimeoutError:
            await session.append("run.failed", {"status":"timed_out","code":"TOTAL_EXECUTION_TIMEOUT","message":"Run exceeded the total execution timeout"})
        except RunFailure as error:
            await session.append("run.failed", {"status":"failed","code":error.code,"message":error.message})
        except Exception:
            await session.append("run.failed", {"status":"failed","code":"LLM_REQUEST_FAILED","message":"LLM execution failed"})

    async def _agent_loop(self, session: RunSession) -> None:
        request=session.request; agent=request.configuration_snapshot["agent"]
        definitions=[ToolDefinition(tool_name=tool["tool_name"], description=tool["description"], input_schema=tool["input_schema"], source_type=tool["source_type"], source_id=tool["source_id"], enabled=tool.get("enabled",True)) for tool in request.configuration_snapshot.get("tools",[]) if tool.get("enabled",True)]
        registry=ToolRegistry(definitions)
        state: dict[str,Any]={"input":request.input,"messages":[{"role":"system","content":agent["system_prompt"]},{"role":"user","content":request.input}],"variables":{},"tool_results":[],"rag_context":[],"output":None,"condition_result":None}
        await session.append("run.started", {"status":"running"})
        for iteration in range(request.limits.max_iterations):
            llm_span=str(uuid4()); llm_started=time.perf_counter()
            await session.append("trace.span.started", {"span_id":llm_span,"parent_span_id":None,"span_type":"LLM","name":"agent.llm","input":{"iteration":iteration+1}})
            try:
                result=await self.adapter.respond(state["messages"], registry.descriptor_list(), os.getenv("LLM_MODEL",agent["model_name"]), agent.get("temperature"),agent.get("max_tokens"))
            except Exception:
                await session.append("trace.span.failed", {"span_id":llm_span,"error":{"code":"LLM_REQUEST_FAILED","message":"LLM request failed"},"latency_ms":self._latency(llm_started)})
                raise RunFailure("LLM_REQUEST_FAILED","LLM request failed")
            if result.final_answer is not None:
                state["messages"].append({"role":"assistant","content":result.final_answer})
                async for delta in self.adapter.stream_final(result.final_answer): await session.append("llm.delta", {"delta":delta})
                state["output"]=result.final_answer
                await session.append("trace.span.completed", {"span_id":llm_span,"output":{"answer":result.final_answer},"latency_ms":self._latency(llm_started)})
                await session.append("run.completed", {"status":"completed","output":result.final_answer})
                return
            if not result.tool_calls:
                await session.append("trace.span.failed", {"span_id":llm_span,"error":{"code":"LLM_INVALID_TOOL_CALL","message":"LLM returned no final answer or Tool Call"},"latency_ms":self._latency(llm_started)})
                raise RunFailure("LLM_INVALID_TOOL_CALL","LLM returned an invalid response")
            await session.append("trace.span.completed", {"span_id":llm_span,"output":{"tool_calls":[call.model_dump() for call in result.tool_calls]},"latency_ms":self._latency(llm_started)})
            state["messages"].append({"role":"assistant","tool_calls":[call.model_dump() for call in result.tool_calls]})
            for call in result.tool_calls: await self._execute_tool(session, registry, state, call.tool_call_id, call.tool_name, call.arguments)
        raise RunFailure("AGENT_MAX_ITERATIONS","Agent reached the maximum number of iterations")

    async def _execute_tool(self, session: RunSession, registry: ToolRegistry, state: dict[str,Any], call_id: str, name: str, arguments: dict[str,Any]) -> None:
        started=time.perf_counter(); span_id=str(uuid4()); event_data={"tool_call_id":call_id,"tool_name":name,"input":arguments}
        await session.append("tool.started", event_data)
        await session.append("trace.span.started", {"span_id":span_id,"parent_span_id":None,"span_type":"TOOL","name":name,"input":{"tool_call_id":call_id,"arguments":arguments}})
        result=await registry.execute(call_id,name,arguments,session.request.limits.tool_timeout_seconds); payload={**event_data,"latency_ms":self._latency(started)}
        if result.status == "failed":
            payload["error"]=result.error; await session.append("tool.failed",payload)
            await session.append("trace.span.failed", {"span_id":span_id,"error":result.error,"latency_ms":self._latency(started)})
            raise RunFailure(result.error["code"],result.error["message"])
        payload["output"]=result.data; await session.append("tool.completed",payload)
        await session.append("trace.span.completed", {"span_id":span_id,"output":{"tool_call_id":call_id,"data":result.data},"latency_ms":self._latency(started)})
        tool_result={"tool_call_id":call_id,"tool_name":name,"status":"succeeded","data":result.data,"error":None}
        state["tool_results"].append(tool_result); state["messages"].append({"role":"tool","tool_call_id":call_id,"name":name,"content":tool_result})

    @staticmethod
    def _latency(started: float) -> int: return round((time.perf_counter()-started)*1000)
    async def stream_events(self, run_id: UUID, last_event_id: int=0) -> AsyncIterator[RuntimeEvent]:
        session=self.sessions[run_id]; cursor=last_event_id
        while True:
            async with session.condition:
                pending=[event for event in session.events if event.event_id>cursor]
                if not pending and not session.terminal:
                    await session.condition.wait(); continue
                terminal=session.terminal
            for event in pending: cursor=event.event_id; yield event
            if terminal and not pending: return
