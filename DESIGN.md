# Mini Coze V1 — 技术设计

本文基于已确认的 V1 Spec。Spring Boot 是业务配置数据的唯一写入口；Run 异步执行；Spring Boot 与 Python Runtime 通过 HTTP + SSE 集成；Runtime 不直接访问 PostgreSQL。

## 1. 总体设计

```text
React
  │ REST / SSE
  ▼
Spring Boot Platform API
  ├── PostgreSQL：配置、Run、Trace、SSE 事件
  ├── HTTP：向 Runtime 创建/启动 Run
  └── SSE Client：订阅 Runtime 事件，持久化并转发
          │
          ▼
Python Agent Runtime
  ├── Agent Loop
  ├── LangGraph Workflow
  ├── Tool Registry
  ├── RAG / Qdrant
  └── MCP Client（stdio）
```

Spring Boot 是唯一对前端开放的服务。Python Runtime 仅暴露内部网络接口。

V1 保持 React、Spring Boot、Python Runtime、PostgreSQL、LangGraph、LangChain、Qdrant 与 MCP 的既定架构，不引入替代平台、任务队列、对象存储或 Secret Manager。

## 2. Spring Boot 与 Runtime 的 HTTP / SSE 通信

前端调用 `POST /api/agents/{id}/runs` 或 `POST /api/workflows/{id}/runs`。Spring Boot 在一个事务中校验目标配置、创建 `run` 与 `trace`（状态 `queued`）、生成不可变 `configuration_snapshot`；事务提交后向 Runtime 发起内部 HTTP 请求，并立即返回 `202 Accepted`。

```http
POST /internal/v1/runs
Content-Type: application/json
```

```json
{
  "run_id": "uuid",
  "trace_id": "uuid",
  "target_type": "agent",
  "target_id": "uuid",
  "workflow_version": null,
  "input": "什么是 RAG？",
  "configuration_snapshot": {},
  "limits": {
    "max_iterations": 10,
    "tool_timeout_seconds": 30,
    "total_execution_timeout_seconds": 300
  }
}
```

Runtime 返回 `202` 后在后台执行。Spring Boot 随即订阅 Runtime 的事件流：

```http
GET /internal/v1/runs/{run_id}/events
Accept: text/event-stream
```

Runtime 保留当前 Run 的内存事件缓冲，并支持按 `Last-Event-ID` 补发，避免 Spring Boot 在订阅建立前遗漏事件。Spring Boot 接收 Runtime event 后先持久化 `run_event` 和 Trace/Run 变化，再向浏览器的 `GET /api/runs/{runId}/events` 转发。浏览器重连时从已持久化事件重放，客户端断开不取消 Run。

V1 不做 Runtime 任务持久化恢复或跨 Runtime 重启续跑。Runtime 重启、进程退出或内部 SSE 流不可恢复时，Spring Boot 将尚未处于终态的关联 Run 标记为 `failed`，错误码为 `RUNTIME_UNAVAILABLE`。

## 3. 数据库实体与表结构

使用 PostgreSQL、UUID 主键、`timestamptz` 时间字段和 `jsonb` 配置字段。

### 配置域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `agents` | `id`, `name`, `description`, `system_prompt`, `model_provider`, `model_name`, `temperature`, `max_tokens` | Agent 基础配置 |
| `agent_tool_bindings` | `agent_id`, `tool_definition_id`, `enabled` | Agent 显式授权 Tool |
| `agent_knowledge_base_bindings` | `agent_id`, `knowledge_base_id` | Agent 显式授权 KB |
| `workflows` | `id`, `name`, `description`, `latest_version` | Workflow 元数据 |
| `workflow_definitions` | `workflow_id`, `version`, `definition_json`, `created_at` | 不可变版本；`(workflow_id, version)` 唯一 |
| `knowledge_bases` | `id`, `name`, `description`, `embedding_config` | KB 元数据 |
| `documents` | `id`, `knowledge_base_id`, `filename`, `content_type`, `storage_key`, `status`, `chunk_count`, `error_message` | 文档与 ingestion 状态 |
| `document_chunks` | `id`, `document_id`, `chunk_index`, `page_number`, `text`, `qdrant_point_id` | 删除、审计和统计 |
| `mcp_servers` | `id`, `name`, `transport`, `configuration`, `enabled` | V1 transport 固定为 `stdio` |
| `tool_definitions` | `id`, `tool_name`, `description`, `input_schema`, `source_type`, `source_id`, `remote_tool_name`, `enabled`, `metadata` | 统一 Tool Catalog；`tool_name` 全局唯一 |

`mcp_servers.configuration` 只保存 command、arguments 和环境变量引用名，绝不保存 Secret 明文。

所有 Secret（包括 LLM、Embedding、MCP 和预配置 HTTP Tool 所需凭据）仅通过环境变量注入；数据库只保存 environment reference，V1 不引入 Secret Manager。

### 执行与可观测性域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `runs` | `id`, `trace_id`, `target_type`, `target_id`, `workflow_version`, `status`, `input`, `output`, `configuration_snapshot`, `started_at`, `ended_at`, `error_code`, `error_message` | 一次异步执行 |
| `traces` | `id`, `run_id`, `status`, `started_at`, `ended_at`, `total_latency_ms` | 一次 Run 一条 Trace |
| `trace_spans` | `id`, `trace_id`, `parent_span_id`, `sequence_no`, `span_type`, `name`, `status`, `input`, `output`, `error`, `started_at`, `ended_at`, `latency_ms`, `token_usage` | LLM、Tool、MCP、RAG、Workflow Node |
| `run_events` | `id`, `run_id`, `event_type`, `payload`, `created_at` | SSE 可重放事件；`id` 作为 SSE Event ID |

`runs.configuration_snapshot` 保证已创建的 Run 不受 Agent、Tool 或 MCP 后续修改影响。Workflow 额外固定 `workflow_version`。

## 4. Agent、Workflow、Run、Trace 数据模型

Agent 只保存配置，不保存执行状态。创建 Run 时，Spring Boot 解析 Agent 基础配置、允许的 Native/MCP Tools、已绑定 KB、`knowledge_search` Tool、MCP Server 配置引用、模型参数和 Runtime 限制，形成 Runtime Snapshot。

LLM 与 Embedding 均通过 Adapter 抽象接入。业务模块只依赖 `LLMAdapter` 与 `EmbeddingAdapter` 接口，不依赖任何具体 Provider SDK；Runtime 通过环境变量配置可用 Provider、Model 和凭据，例如 `LLM_PROVIDER`、`LLM_MODEL`、`EMBEDDING_PROVIDER`、`EMBEDDING_MODEL`。Agent 的 `model_provider` 与 `model_name` 作为平台配置中的逻辑选择值，必须在创建 Run 时映射并校验到已启用的 Adapter 配置。

Workflow Definition 建议采用：

```json
{
  "workflow_id": "uuid",
  "version": 3,
  "nodes": [
    {
      "id": "start",
      "type": "start",
      "configuration": {}
    },
    {
      "id": "llm_1",
      "type": "llm",
      "configuration": {
        "system_prompt": "…",
        "input_path": "$.input",
        "output_path": "$.variables.draft"
      }
    }
  ],
  "edges": [
    { "source": "start", "target": "llm_1" }
  ]
}
```

`PUT /api/workflows/{id}` 创建 `latest_version + 1`，不覆盖旧 Definition。Run 可指定版本；未指定时固定创建时的 `latest_version`。

Run 状态：

```text
queued → running → completed
                 ↘ failed
                 ↘ timed_out
                 ↘ max_iterations
```

Trace 层级：

```text
Trace
├── Workflow Node（仅 Workflow）
│   └── LLM / Tool / RAG / Condition
├── Tool
│   └── MCP
└── LLM
```

所有 Span 在 Runtime 产生，Spring Boot 持久化；写入前统一脱敏和长度截断。

## 5. 统一 State Schema

Agent 和 Workflow 共用 State。Run/Trace ID、限额、工具定义等执行元数据位于 `ExecutionContext`，不进入 State。

```json
{
  "input": "string",
  "messages": [],
  "variables": {},
  "tool_results": [],
  "rag_context": [],
  "output": null,
  "condition_result": null
}
```

| 字段 | 写入者 | 用途 |
|---|---|---|
| `input` | Start / Runtime 初始化 | 用户原始输入，只读 |
| `messages` | Agent Loop、LLM Node | 标准化 LLM 消息 |
| `variables` | Workflow Node | 节点间确定性 JSON 数据 |
| `tool_results` | Tool Registry | Tool Call 的结构化结果 |
| `rag_context` | RAG Node、Knowledge Search Tool | 检索片段 |
| `output` | End、Agent Final Response | 最终响应 |
| `condition_result` | Condition Node | 当前条件结果 |

保留字段不可由 Workflow 任意覆盖；Workflow Node 只能写入其 `output_path` 指定的 `variables` 路径，或由节点类型明确拥有的字段。

## 6. Tool Registry

Runtime 内部抽象：

```text
ToolDefinition
- tool_name: string
- description: string
- input_schema: JSON Schema
- source_type: native | mcp
- source_id: string
- executor: ToolExecutor
```

执行顺序：

```text
LLM / Workflow Tool Node
  → allow-list 校验
  → JSON Schema 校验
  → 创建 Tool Span
  → timeout 包裹 executor
  → 标准化 ToolResult
  → 写入 State.tool_results
```

```json
{
  "tool_call_id": "call_xxx",
  "tool_name": "calculator",
  "status": "succeeded",
  "data": {},
  "error": null
}
```

失败结果使用 `status: "failed"` 并携带结构化 `error.code` 与安全消息。Native Tool 包括受限 Calculator、仅允许预配置公开 GET endpoint 的 `http_get`、一个 Utility Tool，以及校验 KB 授权的 `knowledge_search`。

## 7. LangGraph Workflow 映射

Runtime 为每次 Workflow Run 从快照构建新的 LangGraph，不缓存跨 Run 的可变 Graph。

| Workflow Node | LangGraph 映射 | State 读写 |
|---|---|---|
| Start | Entry node | 初始化 State |
| End | Finish node | 读取 `output` 返回 |
| LLM | async callable | 读取 input/variables/context，写 messages 或 output path |
| Tool | async callable | 参数模板解析、执行 Registry Tool、写 result path |
| RAG | async callable | 检索指定 KB，写 `rag_context` 或 output path |
| Condition | conditional edge | 计算布尔值，选择 A/B |
| HTTP Request | V1 可选 | 如实现，复用受限 `http_get` Tool |

Condition 不使用脚本表达式，采用受限结构化规则：

```json
{
  "left_path": "$.variables.approved",
  "operator": "equals",
  "right_value": true
}
```

V1 支持 `equals`、`not_equals`、`exists`、`truthy`。Condition 输出写入 `condition_result`，并映射至两条明确的 Edge。

## 8. RAG Pipeline

```text
Document Upload
 → Spring Boot 创建 Document(status=uploaded)
 → Runtime ingestion HTTP 请求
 → TXT / Markdown / 可提取文本 PDF 解析
 → Chunking
 → Embedding
 → Qdrant upsert
 → Spring Boot 更新 document status/chunk_count
```

- 原始文件存储在 Docker volume；PostgreSQL 保存 `storage_key`。
- Qdrant collection 按 KB 划分：`kb_{knowledge_base_id}`。
- Qdrant payload 保存 `knowledge_base_id`、`document_id`、`filename`、`chunk_id`、`page_number`、`text`。
- 默认 Top-K 是 5。
- PDF 仅支持可提取文本；无法提取文本时 `Document.status = failed`。
- 删除 Document 时先删除 Qdrant points，再删除元数据。

V1 不引入 S3、MinIO 或其他对象存储；Docker Compose 必须为 Spring Boot 和 Runtime 挂载同一个受控文件 Volume。

Agent 仅经 `knowledge_search` Tool 检索；Workflow 仅经 RAG Node 检索；两种路径均产生 RAG Span。

## 9. MCP Client 与 Tool 映射

V1 只支持 stdio：

```json
{
  "transport": "stdio",
  "configuration": {
    "command": "npx",
    "arguments": ["-y", "@example/server"],
    "environment_references": ["EXAMPLE_API_KEY"]
  }
}
```

连接流程：

```text
Spring Boot POST /api/mcp/servers/{id}/connect
  → Runtime 内部 HTTP discovery
  → Runtime 启动 stdio Client 并 list_tools
  → 返回 Remote Tool descriptors
  → Spring Boot 写入/更新 tool_definitions
```

MCP Tool 规范名：

```text
mcp__{server_id_normalized}__{remote_tool_name_normalized}
```

例如 `mcp__weather_server__get_forecast`。Registry 保留 `remote_tool_name`，执行时映射回原 MCP Tool 名称。规范化后冲突则连接失败为 `CONFIGURATION_ERROR`，不可覆盖现有 Tool。MCP Tool Trace 必须为 `Tool Span → MCP Span`。

## 10. SSE Event Schema

所有 SSE event 都有单调递增 ID，且被持久化。

```text
id: 42
event: output.delta
data: {"run_id":"...","trace_id":"...","delta":"RAG 是…"}
```

| Event | 用途 |
|---|---|
| `run.status` | `queued`、`running`、终态变化 |
| `trace.span.started` | Span 开始 |
| `trace.span.completed` | Span 成功结束 |
| `trace.span.failed` | Span 失败 |
| `output.delta` | LLM 输出增量 |
| `run.completed` | 最终 output |
| `run.failed` | 终态错误 |

通用 payload：

```json
{
  "run_id": "uuid",
  "trace_id": "uuid",
  "timestamp": "2026-08-29T10:00:00Z",
  "sequence": 42,
  "data": {}
}
```

SSE 只传播已脱敏的内容。建议每 15 秒发送 `heartbeat`，避免代理静默断连。

## 11. 项目目录结构

```text
mini-coze/
├── apps/
│   └── web/                       # React + TypeScript + React Flow
├── services/
│   ├── platform-api/              # Spring Boot
│   │   └── src/main/java/.../
│   │       ├── agent/
│   │       ├── workflow/
│   │       ├── knowledgebase/
│   │       ├── mcp/
│   │       ├── run/
│   │       ├── trace/
│   │       ├── runtimeclient/     # HTTP/SSE Runtime client
│   │       └── common/
│   └── agent-runtime/             # Python
│       ├── app/
│       │   ├── api/               # internal HTTP/SSE endpoints
│       │   ├── runtime/
│       │   ├── agents/
│       │   ├── workflows/
│       │   ├── tools/
│       │   ├── rag/
│       │   ├── mcp/
│       │   ├── llm/
│       │   ├── trace/
│       │   └── schemas/
│       └── tests/
├── mcp-servers/
│   └── simple-server/             # 自建 stdio MCP Server
├── infra/
│   ├── docker-compose.yml
│   ├── postgres/
│   └── qdrant/
├── specs/
└── README.md
```

## 12. 模块依赖关系

```text
web
 └── platform-api REST/SSE

platform-api
 ├── PostgreSQL
 ├── runtimeclient
 └── Runtime internal API

agent-runtime
 ├── runtime → agents / workflows
 ├── agents → llm / tools / trace
 ├── workflows → langgraph / tools / rag / trace
 ├── tools → rag / mcp
 ├── llm → LLMAdapter → Provider SDK
 ├── rag → EmbeddingAdapter → Qdrant / Embedding Provider SDK
 └── mcp → MCP SDK / stdio processes
```

Runtime 不依赖 Spring Boot 的数据库实体或 Repository；两服务只通过版本化 JSON Contract 通信。

## 13. 错误处理与超时机制

| 分类 | 示例代码 |
|---|---|
| 配置 | `CONFIGURATION_ERROR`, `TOOL_NOT_ALLOWED`, `WORKFLOW_VERSION_NOT_FOUND` |
| LLM | `LLM_REQUEST_FAILED`, `LLM_INVALID_TOOL_CALL` |
| Tool | `TOOL_VALIDATION_ERROR`, `TOOL_EXECUTION_ERROR`, `TOOL_TIMEOUT` |
| RAG | `RAG_INGESTION_FAILED`, `RAG_RETRIEVAL_FAILED` |
| MCP | `MCP_CONNECTION_FAILED`, `MCP_TOOL_NOT_FOUND`, `MCP_EXECUTION_FAILED` |
| Runtime | `RUNTIME_UNAVAILABLE`, `TOTAL_EXECUTION_TIMEOUT`, `MAX_ITERATIONS_REACHED` |

外部 API 返回安全错误：

```json
{
  "code": "TOOL_TIMEOUT",
  "message": "Tool execution timed out",
  "run_id": "uuid",
  "trace_id": "uuid"
}
```

内部异常、堆栈、Secret 和原始第三方错误仅记录在受控日志中，不返回客户端。

- `max_iterations = 10`
- 单 Tool（含 MCP、RAG）`tool_timeout = 30s`
- 单 Run `total_execution_timeout = 300s`
- Runtime HTTP 启动请求短超时，例如 10 秒；该请求只负责接收任务。
- 总超时时，Runtime 取消运行中的协程或 stdio 子进程，发送终态事件；Spring Boot 将 Run 标记为 `timed_out`。
- Tool 失败写入 Tool Span 和结构化 Tool Result；Agent 可继续推理。
- 配置错误或 Runtime 不可用时，Run 终止并标记为 `failed`；Runtime 重启不触发恢复或重试。

## 已确认的 V1 实现约束

1. 原始上传文件使用 Docker Volume 保存，不引入 S3、MinIO 或其他对象存储。
2. Secret 仅通过环境变量注入；数据库只保存 environment reference，V1 不引入 Secret Manager。
3. Runtime 不做任务持久化恢复；Runtime 重启导致未终态 Run 标记为 `failed`，不自动续跑或重试。
4. Workflow Condition 仅支持受限结构化规则：`equals`、`not_equals`、`exists`、`truthy`；不支持脚本、LLM 分类或通用表达式语言。
5. LLM 和 Embedding 通过 Adapter 抽象接入；具体 Provider、Model 和凭据由环境变量配置，业务架构不硬编码具体 Provider。
6. 保持 Spring Boot / Python Runtime / PostgreSQL / LangGraph / LangChain / Qdrant / MCP 的既定架构不变。
