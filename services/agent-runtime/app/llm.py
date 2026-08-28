from __future__ import annotations

import json
import os
from abc import ABC, abstractmethod
from collections.abc import AsyncIterator

import httpx


class LLMAdapter(ABC):
    @abstractmethod
    async def stream(self, system_prompt: str, user_input: str, model: str, temperature: float | None, max_tokens: int | None) -> AsyncIterator[str]:
        """Yield final-answer text deltas. Tool calling is deliberately outside this slice."""


class FakeLLMAdapter(LLMAdapter):
    async def stream(self, system_prompt: str, user_input: str, model: str, temperature: float | None, max_tokens: int | None) -> AsyncIterator[str]:
        response = os.getenv("FAKE_LLM_RESPONSE", f"Fake response: {user_input}")
        for token in response.split(" "):
            yield token + " " if token != response.split(" ")[-1] else token


class OpenAICompatibleAdapter(LLMAdapter):
    async def stream(self, system_prompt: str, user_input: str, model: str, temperature: float | None, max_tokens: int | None) -> AsyncIterator[str]:
        base_url = os.environ["LLM_BASE_URL"].rstrip("/")
        api_key = os.environ["LLM_API_KEY"]
        payload = {
            "model": model,
            "stream": True,
            "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_input}],
        }
        if temperature is not None:
            payload["temperature"] = temperature
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens
        async with httpx.AsyncClient(timeout=60) as client:
            async with client.stream("POST", f"{base_url}/v1/chat/completions", headers={"Authorization": f"Bearer {api_key}"}, json=payload) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    content = line[6:]
                    if content == "[DONE]":
                        return
                    choice = json.loads(content).get("choices", [{}])[0]
                    delta = choice.get("delta", {}).get("content")
                    if delta:
                        yield delta


def create_llm_adapter() -> LLMAdapter:
    provider = os.getenv("LLM_PROVIDER", "fake").lower()
    if provider == "fake":
        return FakeLLMAdapter()
    if provider in {"openai", "openai_compatible"}:
        return OpenAICompatibleAdapter()
    raise ValueError(f"Unsupported LLM_PROVIDER: {provider}")
