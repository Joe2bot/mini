import asyncio
from uuid import uuid4

from app.llm import FakeLLMAdapter
from app.runtime import RunManager
from app.schemas import RuntimeStartRequest
from app.tools import ToolDefinition, ToolRegistry, _validate_public_url


CALCULATOR = ToolDefinition("calculator", "math", {"type": "object", "properties": {"expression": {"type": "string"}}, "required": ["expression"], "additionalProperties": False}, "native", "calculator")


def run(coroutine): return asyncio.run(coroutine)


def test_fake_llm_streams_direct_final_answer(monkeypatch):
    monkeypatch.setenv("FAKE_LLM_RESPONSE", "hello streaming world")
    async def collect():
        adapter = FakeLLMAdapter(); result = await adapter.respond([{"role": "user", "content": "input"}], [], "fake", None, None)
        return "".join([delta async for delta in adapter.stream_final(result.final_answer)])
    assert run(collect()) == "hello streaming world"


def test_registry_calculates_and_rejects_invalid_arguments():
    registry = ToolRegistry([CALCULATOR])
    assert run(registry.execute("call-1", "calculator", {"expression": "2 * (3 + 4)"}, 1)).data == {"result": 14}
    invalid = run(registry.execute("call-2", "calculator", {"expression": "__import__('os')"}, 1))
    assert invalid.error["code"] == "CALCULATOR_INVALID_EXPRESSION"
    schema_invalid = run(registry.execute("call-3", "calculator", {}, 1))
    assert schema_invalid.error["code"] == "TOOL_VALIDATION_ERROR"


def test_registry_rejects_unauthorized_tool_and_timeout():
    assert run(ToolRegistry([]).execute("call", "calculator", {"expression": "1+1"}, 1)).error["code"] == "TOOL_NOT_ALLOWED"
    async def slow(_, __): await asyncio.sleep(0.05)
    slow_definition = ToolDefinition("slow", "slow", {"type": "object"}, "native", "slow")
    timeout = run(ToolRegistry([slow_definition], {"slow": slow}).execute("call", "slow", {}, 0.001))
    assert timeout.error["code"] == "TOOL_TIMEOUT"


def test_http_tool_rejects_localhost_before_request():
    try: _validate_public_url("http://127.0.0.1/metadata")
    except Exception as error: assert getattr(error, "code") == "HTTP_URL_NOT_ALLOWED"
    else: assert False, "localhost must be rejected"


def request(tools, max_iterations=10):
    return RuntimeStartRequest(run_id=uuid4(), trace_id=uuid4(), target_type="agent", target_id=uuid4(), input="hi", limits={"max_iterations": max_iterations, "tool_timeout_seconds": 1, "total_execution_timeout_seconds": 2}, configuration_snapshot={"agent": {"system_prompt": "be helpful", "model_name": "fake"}, "tools": tools})


def events_for(monkeypatch, scenario, tools, max_iterations=10):
    monkeypatch.setenv("FAKE_LLM_SCENARIO", scenario)
    async def execute():
        item = request(tools, max_iterations); manager = RunManager(FakeLLMAdapter()); await manager.start(item); await asyncio.sleep(0.05)
        return [event async for event in manager.stream_events(item.run_id)]
    return run(execute())


def test_agent_tool_call_appends_result_and_completes(monkeypatch):
    events = events_for(monkeypatch, "tool", [CALCULATOR.__dict__])
    types = [event.event_type for event in events]
    assert "tool.started" in types and "tool.completed" in types and types[-1] == "run.completed"
    assert [event.data for event in events if event.event_type == "tool.completed"][0]["output"] == {"result": 4}


def test_agent_supports_multiple_tool_calls_and_max_iterations(monkeypatch):
    length = ToolDefinition("text_length", "length", {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"]}, "native", "text_length")
    multi = events_for(monkeypatch, "multi_tool", [CALCULATOR.__dict__, length.__dict__])
    assert [event.event_type for event in multi].count("tool.completed") == 2
    loop = events_for(monkeypatch, "max_iterations", [CALCULATOR.__dict__], max_iterations=2)
    assert loop[-1].data["code"] == "AGENT_MAX_ITERATIONS"
