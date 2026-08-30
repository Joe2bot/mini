# Mini Coze V1 — Phase 2 Report: Tool Calling

## 1. 已实现功能

- Native Tool 的 CRUD、全局唯一名称与标准 JSON Schema 配置。
- `PUT /api/agents/{agentId}/tools` Agent Tool allow-list；仅启用且绑定的 Tool 进入 Run Snapshot。
- Runtime Tool Registry：allow-list resolve、Draft 2020-12 JSON Schema 校验、超时包裹与结构化错误。
- Native `calculator`（AST 白名单，不使用 `eval`）、`text_length`、`current_timestamp` 与预配置 endpoint 的 `http_get`。
- LLM Adapter 的 Final Answer / Structured Tool Call 表示；Fake Adapter 支持直接回答、Tool→Result→Final、多 Tool、未授权、非法参数、max iterations。
- Agent Loop：LLM → Tool → State `tool_results` / messages → LLM，按顺序执行；具备 iteration、Tool 与总执行超时。
- 新增 Tool SSE：`tool.started`、`tool.completed`、`tool.failed`；保留 Phase 1 replay / Last-Event-ID 机制。
- Trace 通过既有 Runtime Event 持久化为 `LLM → TOOL → LLM` spans；前端可绑定 Tool 并查看 Tool Execution。

## 2. 新增/修改文件

- Platform：`tool/*`、`agent/AgentToolBinding*`、`agent/AgentController.java`、`agent/AgentService.java`、`run/RunService.java`。
- Runtime：`app/tools.py`、`app/llm.py`、`app/runtime.py`、`app/schemas.py`、`requirements.txt`、`tests/test_runtime.py`。
- Frontend：`apps/web/src/api.ts`、`apps/web/src/main.tsx`、`apps/web/src/styles.css`。
- Tests：`ToolApiIntegrationTest.java`、更新 `RunApiIntegrationTest.java` 与 `AgentServiceTest.java`。
- Docs/config：`README.md`、`infra/docker-compose.yml`、本报告。

## 3. 项目目录变化

```text
services/platform-api/src/main/java/com/minicoze/platform/
├── tool/                         # Tool definition CRUD
└── agent/AgentToolBinding*        # Agent allow-list

services/agent-runtime/app/
└── tools.py                       # Registry and Native executors
```

## 4. Tool 数据模型

`tool_definitions`：`id`、`tool_name`、`description`、`input_schema`、`source_type`、`source_id`、`enabled`、时间字段。

`agent_tool_bindings`：`agent_id`、`tool_id`、`enabled`，并对 `(agent_id, tool_id)` 唯一。

`source_id` 只保存 Runtime 环境变量引用；不会保存 endpoint URL、API Key 或 Authorization 值。

## 5. Tool Registry 设计

`ToolRegistry` 从 Run Snapshot 构造，不访问 PostgreSQL。其执行顺序是：resolve allow-list → Draft 2020-12 Schema validation → executor（`asyncio.wait_for`）→ `ToolResult` → State / LLM messages。失败使用 `TOOL_NOT_ALLOWED`、`TOOL_VALIDATION_ERROR`、`TOOL_TIMEOUT` 或 `TOOL_EXECUTION_ERROR`。

## 6. JSON Schema 示例

```json
{
  "type": "object",
  "properties": {"expression": {"type": "string"}},
  "required": ["expression"],
  "additionalProperties": false
}
```

## 7. Agent Runtime Loop

```text
LLM (Final Answer) → llm.delta → completed
LLM (Tool Call) → Tool Registry → Tool Result → append state.tool_results/messages → next LLM
```

最大迭代终止错误码为 `AGENT_MAX_ITERATIONS`；Tool 与总 Run timeout 分别由 Run limits 控制。

## 8. API 变化

- `POST /api/tools`
- `GET /api/tools`
- `GET /api/tools/{id}`
- `PUT /api/tools/{id}`
- `DELETE /api/tools/{id}`
- `GET /api/agents/{id}/tools`
- `PUT /api/agents/{id}/tools`，body：`{"toolIds":["uuid"]}`

Phase 1 `POST /api/agents/{id}/runs` 仍返回 `202 Accepted`，语义未变。

## 9. SSE Event Schema

新增事件的 envelope 沿用 Phase 1：`event_id`、`event_type`、`run_id`、`trace_id`、`timestamp`、`data`。

`tool.completed.data` 示例：

```json
{
  "tool_call_id": "call_1",
  "tool_name": "calculator",
  "input": {"expression": "2+2"},
  "output": {"result": 4},
  "latency_ms": 0
}
```

## 10. Trace 结构

Native Tool Run 的 Trace：

```text
Trace
├── LLM (Tool Call)
├── TOOL (tool_call_id, input, output/error, latency)
└── LLM (Final Answer)
```

MCP span 未实现；MCP Tool Definition 不会在 Phase 2 执行。

## 11. Frontend 变化

Agent 选中后可选择已创建且启用的 Native Tool；Run 视图显示 Tool 事件及原有流式输出和 Trace。

## 12. 测试结果

- Python Runtime：`pytest -q` → **6 passed**。
- Spring Boot：`mvn test -q` → **6 passed**。
- Frontend：`npm run build` → **passed**。

## 13. Docker Compose Smoke Test

已通过 Docker Compose 真实执行：Create Tool → Create Agent → Bind Tool → Create Run → Fake LLM calculator Tool Call → SSE → completed → Trace。

- Run：`196218f5-5aa5-4d5e-ab59-d1123f37000c`
- Trace：`89320b7f-fb0a-4413-ab10-1678c218f799`
- 最终输出：`Tool result: {"result": 4}`
- 持久化 spans：LLM → TOOL calculator → LLM。

## 14. Definition of Done

| 项目 | 结果 |
|---|---|
| Tool CRUD / Agent allow-list / Snapshot | 通过 |
| Runtime 不读取 PostgreSQL / Tool Registry / JSON Schema | 通过 |
| calculator / SSRF 防护 HTTP Tool / utility Tool | 通过 |
| Structured Tool Call / Result 回填 / 连续调用 | 通过 |
| Max Iterations / Tool Timeout | 通过 |
| Tool SSE / Trace 持久化 / Frontend 展示 | 通过 |
| Unit、Integration、Compose E2E Smoke | 通过 |
| Phase 1 回归 / Scope 控制 | 通过 |

## 15. 已知问题

- `http_get` 只接受预配置的公网 endpoint；本阶段未为其配置实际第三方 endpoint，SSRF 拒绝路径由自动化测试覆盖。
- Fake LLM 场景通过 Runtime 环境变量控制，仅用于测试；生产 Provider 仍通过 Adapter 与环境变量配置。
- 未实现 MCP、RAG、Workflow 或其他 Phase 3+ 功能。
