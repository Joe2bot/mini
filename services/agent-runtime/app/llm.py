from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from collections.abc import AsyncIterator
from typing import Any
from uuid import uuid4

import httpx

from app.schemas import LLMResult, ToolCall


class LLMAdapter(ABC):
    @abstractmethod
    async def respond(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]], model: str,
                      temperature: float | None, max_tokens: int | None) -> LLMResult:
        """Produce either a final answer or structured Tool Calls."""

    async def stream_final(self, answer: str) -> AsyncIterator[str]:
        words = answer.split(" ")
        for index, word in enumerate(words):
            yield word if index == len(words) - 1 else word + " "


class FakeLLMAdapter(LLMAdapter):
    async def respond(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]], model: str,
                      temperature: float | None, max_tokens: int | None) -> LLMResult:
        scenario = os.getenv("FAKE_LLM_SCENARIO", "direct").lower()
        tool_results = [message for message in messages if message.get("role") == "tool"]
        call_count = len([message for message in messages if message.get("role") == "assistant" and message.get("tool_calls")])
        if scenario == "direct":
            return LLMResult(final_answer=os.getenv("FAKE_LLM_RESPONSE", f"Fake response: {messages[-1]['content']}"))
        if scenario == "unauthorized":
            return LLMResult(tool_calls=[ToolCall(tool_call_id="fake-unauthorized", tool_name="not_allowed", arguments={})])
        if scenario == "invalid_arguments":
            return LLMResult(tool_calls=[ToolCall(tool_call_id="fake-invalid", tool_name="calculator", arguments={})])
        if scenario == "max_iterations":
            return LLMResult(tool_calls=[ToolCall(tool_call_id=f"fake-loop-{call_count}", tool_name="calculator", arguments={"expression": "1+1"})])
        if scenario == "multi_tool":
            if call_count == 0:
                return LLMResult(tool_calls=[ToolCall(tool_call_id="fake-calc", tool_name="calculator", arguments={"expression": "2+3"})])
            if call_count == 1:
                return LLMResult(tool_calls=[ToolCall(tool_call_id="fake-length", tool_name="text_length", arguments={"text": "mini"})])
            return LLMResult(final_answer="Completed two tool calls.")
        tool_name = os.getenv("FAKE_LLM_TOOL_NAME", "calculator")
        arguments = json.loads(os.getenv("FAKE_LLM_TOOL_ARGUMENTS", '{"expression":"2+2"}'))
        if not tool_results:
            return LLMResult(tool_calls=[ToolCall(tool_call_id="fake-tool-call", tool_name=tool_name, arguments=arguments)])
        result = tool_results[-1].get("content", {})
        return LLMResult(final_answer=f"Tool result: {json.dumps(result.get('data'), ensure_ascii=False)}")


class OpenAICompatibleAdapter(LLMAdapter):
    async def respond(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]], model: str,
                      temperature: float | None, max_tokens: int | None) -> LLMResult:
        base_url = os.environ["LLM_BASE_URL"].rstrip("/")
        normalized_messages = []
        for message in messages:
            if message.get("role") == "assistant" and message.get("tool_calls"):
                normalized_messages.append({"role": "assistant", "tool_calls": [{"id": call["tool_call_id"], "type": "function", "function": {"name": call["tool_name"], "arguments": json.dumps(call["arguments"])}} for call in message["tool_calls"]]})
            elif message.get("role") == "tool":
                normalized_messages.append({"role": "tool", "tool_call_id": message["tool_call_id"], "content": json.dumps(message["content"], ensure_ascii=False)})
            else:
                normalized_messages.append(message)
        payload: dict[str, Any] = {"model": model, "stream": False, "messages": normalized_messages}
        if tools:
            payload["tools"] = [{"type": "function", "function": {"name": item["tool_name"], "description": item["description"], "parameters": item["input_schema"]}} for item in tools]
        if temperature is not None: payload["temperature"] = temperature
        if max_tokens is not None: payload["max_tokens"] = max_tokens
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(f"{base_url}/v1/chat/completions", headers={"Authorization": f"Bearer {os.environ['LLM_API_KEY']}"}, json=payload)
            response.raise_for_status()
        message = response.json()["choices"][0]["message"]
        calls = []
        for call in message.get("tool_calls", []):
            function = call.get("function", {})
            try: arguments = json.loads(function.get("arguments", "{}"))
            except json.JSONDecodeError: raise ValueError("LLM returned invalid Tool Call arguments")
            calls.append(ToolCall(tool_call_id=call.get("id", str(uuid4())), tool_name=function.get("name", ""), arguments=arguments))
        return LLMResult(final_answer=message.get("content") if not calls else None, tool_calls=calls)


def create_llm_adapter() -> LLMAdapter:
    provider = os.getenv("LLM_PROVIDER", "fake").lower()
    if provider == "fake": return FakeLLMAdapter()
    if provider in {"openai", "openai_compatible"}: return OpenAICompatibleAdapter()
    raise ValueError(f"Unsupported LLM_PROVIDER: {provider}")
