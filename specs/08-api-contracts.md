# API Contract 规格

以下是 V1 的逻辑接口，具体框架实现细节可以在 Design 阶段确定。

## Agent

POST /api/agents
GET /api/agents
GET /api/agents/{id}
PUT /api/agents/{id}
DELETE /api/agents/{id}

POST /api/agents/{id}/runs

请求：

{
  "input": "什么是 RAG？"
}

返回：

{
  "run_id": "run_xxx",
  "trace_id": "trace_xxx",
  "status": "queued"
}

Run 创建为异步操作，成功响应为 `202 Accepted`。最终 `output` 和状态通过 Run 查询或 SSE 获取。

## Workflow

POST /api/workflows
GET /api/workflows
GET /api/workflows/{id}
PUT /api/workflows/{id}
DELETE /api/workflows/{id}

POST /api/workflows/{id}/runs

请求可选指定版本：

```json
{
  "input": "...",
  "version": 3
}
```

未指定 `version` 时，Spring Boot 在创建 Run 时固定当前 Workflow version；响应必须包含固定的 `workflow_version`。

Workflow Run 同样为异步操作，成功响应为 `202 Accepted`，并返回 `run_id`、`trace_id`、`status: "queued"` 和 `workflow_version`。

## Knowledge Base

POST /api/knowledge-bases
GET /api/knowledge-bases
POST /api/knowledge-bases/{id}/documents

## MCP

POST /api/mcp/servers
GET /api/mcp/servers
POST /api/mcp/servers/{id}/connect
GET /api/mcp/servers/{id}/tools

## Trace

GET /api/traces/{traceId}
GET /api/runs/{runId}
GET /api/runs/{runId}/events

`GET /api/runs/{runId}/events` 是 `text/event-stream` SSE 接口，用于推送状态、Trace Step 和输出增量事件。Spring Boot 将 Runtime SSE 事件持久化后转发；客户端断开不取消 Run。

`GET /api/runs/{runId}` 至少返回 `run_id`、`trace_id`、`status`、目标 Agent/Workflow、固定的 `workflow_version`（如适用）及完成后的 `output`。V1 状态为 `queued`、`running`、`completed`、`failed`、`timed_out`、`max_iterations`。

## Error Contract

统一错误结构：

{
  "code": "TOOL_TIMEOUT",
  "message": "Tool execution timed out",
  "run_id": "run_xxx",
  "trace_id": "trace_xxx"
}

具体 Error Code 可以在实现阶段细化，但 API 不允许直接向客户端返回 Stack Trace。

Spring Boot 是 Agent、Workflow、Knowledge Base、Document 和 MCP Server 等业务配置数据的唯一写入口。Runtime 与 Spring Boot 的内部 HTTP/SSE 接口不直接暴露给客户端。
