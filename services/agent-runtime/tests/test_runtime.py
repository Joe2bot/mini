import asyncio
from uuid import uuid4

from app.llm import FakeLLMAdapter
from app.runtime import RunManager
from app.schemas import RuntimeStartRequest


def test_fake_llm_streams_configured_answer(monkeypatch):
    monkeypatch.setenv("FAKE_LLM_RESPONSE", "hello streaming world")
    async def collect():
        return [delta async for delta in FakeLLMAdapter().stream("system", "input", "fake", None, None)]
    assert "".join(asyncio.run(collect())) == "hello streaming world"


def test_agent_run_emits_llm_trace_and_completion(monkeypatch):
    monkeypatch.setenv("FAKE_LLM_RESPONSE", "done")
    request = RuntimeStartRequest(run_id=uuid4(), trace_id=uuid4(), target_type="agent", target_id=uuid4(), input="hi", configuration_snapshot={"agent": {"system_prompt": "be helpful", "model_name": "fake"}})
    async def execute():
        manager = RunManager(FakeLLMAdapter())
        await manager.start(request)
        await asyncio.sleep(0.02)
        return [event.event_type async for event in manager.stream_events(request.run_id)]
    assert asyncio.run(execute()) == ["run.started", "trace.span.started", "llm.delta", "trace.span.completed", "run.completed"]
