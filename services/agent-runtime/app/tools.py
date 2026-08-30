from __future__ import annotations

import ast
import asyncio
import ipaddress
import json
import os
import socket
from dataclasses import dataclass
from typing import Any, Awaitable, Callable
from urllib.parse import parse_qsl, urlencode, urljoin, urlsplit, urlunsplit

import httpx
from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError


@dataclass(frozen=True)
class ToolDefinition:
    tool_name: str
    description: str
    input_schema: dict[str, Any]
    source_type: str
    source_id: str
    enabled: bool = True


@dataclass(frozen=True)
class ToolResult:
    tool_call_id: str
    tool_name: str
    status: str
    data: Any = None
    error: dict[str, str] | None = None


class ToolExecutionError(Exception):
    def __init__(self, code: str, message: str):
        self.code = code
        self.message = message
        super().__init__(message)


Executor = Callable[[ToolDefinition, dict[str, Any]], Awaitable[Any]]


class ToolRegistry:
    def __init__(self, definitions: list[ToolDefinition], executors: dict[str, Executor] | None = None):
        self.definitions = {item.tool_name: item for item in definitions if item.source_type == "native" and item.enabled}
        self.executors = executors or {"calculator": calculator, "text_length": text_length, "current_timestamp": current_timestamp, "http_get": http_get}

    def descriptor_list(self) -> list[dict[str, Any]]:
        return [{"tool_name": d.tool_name, "description": d.description, "input_schema": d.input_schema} for d in self.definitions.values()]

    async def execute(self, tool_call_id: str, tool_name: str, arguments: Any, timeout_seconds: int) -> ToolResult:
        definition = self.definitions.get(tool_name)
        if definition is None:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_NOT_ALLOWED", "message": "Tool is not allowed for this Agent"})
        if not isinstance(arguments, dict):
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_VALIDATION_ERROR", "message": "Tool arguments must be a JSON object"})
        try:
            errors = sorted(Draft202012Validator(definition.input_schema).iter_errors(arguments), key=lambda error: list(error.path))
        except SchemaError:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_VALIDATION_ERROR", "message": "Tool input schema is invalid"})
        if errors:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_VALIDATION_ERROR", "message": "Tool arguments do not match the input schema"})
        executor = self._executor_for(definition)
        if executor is None:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_EXECUTION_ERROR", "message": "Native tool executor is not available"})
        try:
            return ToolResult(tool_call_id, tool_name, "succeeded", data=await asyncio.wait_for(executor(definition, arguments), timeout=timeout_seconds))
        except asyncio.TimeoutError:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_TIMEOUT", "message": "Tool execution timed out"})
        except ToolExecutionError as error:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": error.code, "message": error.message})
        except Exception:
            return ToolResult(tool_call_id, tool_name, "failed", error={"code": "TOOL_EXECUTION_ERROR", "message": "Tool execution failed"})

    def _executor_for(self, definition: ToolDefinition) -> Executor | None:
        if definition.tool_name in self.executors:
            return self.executors[definition.tool_name]
        if definition.source_id.endswith("_ENDPOINT"):
            return self.executors.get("http_get")
        return None


def _calculate(node: ast.AST) -> float:
    if isinstance(node, ast.Expression): return _calculate(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)) and not isinstance(node.value, bool): return float(node.value)
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _calculate(node.operand); return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Sub, ast.Mult, ast.Div)):
        left, right = _calculate(node.left), _calculate(node.right)
        if isinstance(node.op, ast.Add): return left + right
        if isinstance(node.op, ast.Sub): return left - right
        if isinstance(node.op, ast.Mult): return left * right
        return left / right
    raise ToolExecutionError("CALCULATOR_INVALID_EXPRESSION", "Expression supports only numbers and basic arithmetic")


async def calculator(_: ToolDefinition, arguments: dict[str, Any]) -> dict[str, Any]:
    expression = arguments.get("expression")
    if not isinstance(expression, str) or len(expression) > 200: raise ToolExecutionError("CALCULATOR_INVALID_EXPRESSION", "Expression is invalid")
    try: result = _calculate(ast.parse(expression, mode="eval"))
    except (SyntaxError, ValueError, ZeroDivisionError, OverflowError): raise ToolExecutionError("CALCULATOR_INVALID_EXPRESSION", "Expression is invalid")
    if not result == result or result in (float("inf"), float("-inf")): raise ToolExecutionError("CALCULATOR_INVALID_EXPRESSION", "Expression result is invalid")
    return {"result": int(result) if result.is_integer() else result}


async def text_length(_: ToolDefinition, arguments: dict[str, Any]) -> dict[str, Any]: return {"length": len(arguments["text"])}


async def current_timestamp(_: ToolDefinition, arguments: dict[str, Any]) -> dict[str, Any]:
    from datetime import datetime, timezone
    return {"timestamp": datetime.now(timezone.utc).isoformat()}


def _validate_public_url(url: str) -> None:
    parsed = urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname: raise ToolExecutionError("HTTP_URL_NOT_ALLOWED", "Configured endpoint must use http or https")
    host = parsed.hostname.lower().rstrip(".")
    if host == "localhost" or host.endswith(".localhost"): raise ToolExecutionError("HTTP_URL_NOT_ALLOWED", "Configured endpoint host is not allowed")
    allowed = {item.strip().lower() for item in os.getenv("HTTP_GET_ALLOWED_HOSTS", "").split(",") if item.strip()}
    if allowed and host not in allowed: raise ToolExecutionError("HTTP_URL_NOT_ALLOWED", "Configured endpoint host is not allow-listed")
    try: addresses = socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM)
    except socket.gaierror: raise ToolExecutionError("HTTP_URL_NOT_ALLOWED", "Configured endpoint host cannot be resolved")
    for address in addresses:
        if not ipaddress.ip_address(address[4][0]).is_global: raise ToolExecutionError("HTTP_URL_NOT_ALLOWED", "Configured endpoint resolves to a restricted address")


def _with_business_parameters(url: str, parameters: dict[str, Any]) -> str:
    parsed = urlsplit(url); query = list(parse_qsl(parsed.query, keep_blank_values=True))
    for key, value in parameters.items():
        if not isinstance(value, (str, int, float, bool)): raise ToolExecutionError("TOOL_VALIDATION_ERROR", "HTTP parameters must be scalar values")
        query.append((key, str(value)))
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, urlencode(query), ""))


async def http_get(definition: ToolDefinition, arguments: dict[str, Any]) -> dict[str, Any]:
    endpoint = os.getenv(definition.source_id)
    if not endpoint: raise ToolExecutionError("CONFIGURATION_ERROR", "Configured HTTP endpoint is unavailable")
    url = _with_business_parameters(endpoint, arguments); max_redirects=max(0,int(os.getenv("HTTP_GET_MAX_REDIRECTS","0"))); max_bytes=max(1,int(os.getenv("HTTP_GET_MAX_RESPONSE_BYTES","1048576"))); timeout=float(os.getenv("HTTP_GET_TIMEOUT_SECONDS","10"))
    for redirect_count in range(max_redirects + 1):
        _validate_public_url(url)
        async with httpx.AsyncClient(follow_redirects=False, timeout=timeout) as client:
            async with client.stream("GET", url, headers={"Accept":"application/json, text/plain;q=0.9, */*;q=0.1"}) as response:
                if response.is_redirect:
                    if redirect_count >= max_redirects or not response.headers.get("location"): raise ToolExecutionError("HTTP_REDIRECT_NOT_ALLOWED", "HTTP redirect is not allowed")
                    url=urljoin(url,response.headers["location"]); continue
                response.raise_for_status()
                declared_length = response.headers.get("content-length")
                if declared_length and int(declared_length) > max_bytes: raise ToolExecutionError("HTTP_RESPONSE_TOO_LARGE", "HTTP response exceeds the configured size limit")
                chunks = bytearray()
                async for chunk in response.aiter_bytes():
                    chunks.extend(chunk)
                    if len(chunks) > max_bytes: raise ToolExecutionError("HTTP_RESPONSE_TOO_LARGE", "HTTP response exceeds the configured size limit")
                content_type=response.headers.get("content-type",""); raw=bytes(chunks)
                try: body: Any = json.loads(raw) if "application/json" in content_type else raw.decode(response.encoding or "utf-8", errors="replace")
                except ValueError: body=raw.decode(response.encoding or "utf-8", errors="replace")
                return {"status_code":response.status_code,"content_type":content_type,"body":body}
    raise ToolExecutionError("HTTP_REDIRECT_NOT_ALLOWED", "HTTP redirect is not allowed")
