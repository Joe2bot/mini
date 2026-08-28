# Trace / Observability 规格

## 1. 目的

记录每一次 Agent / Workflow 执行的完整轨迹。

V1 重点是 Execution Trace，不要求企业级 Observability。

## 2. Trace 模型

Trace 表示一次完整执行。

必须包含：
- trace_id
- run_id
- agent_id 或 workflow_id
- status
- start_time
- end_time
- total_latency

每个 Step / Span 包含：
- span_id
- parent_span_id（如果存在）
- node_type
- name
- status
- input
- output
- start_time
- end_time
- latency
- error
- token usage（如果可以获取）

## 3. Step 类型

至少：
- LLM
- Tool
- MCP
- RAG
- Workflow Node

当 Tool 的 `source_type` 为 `mcp` 时，Trace 层级必须为 `Tool` Span 的子 `MCP` Span：

```text
Tool
└── MCP
```

`Tool` Span 记录统一 Registry 的校验与执行结果；`MCP` Span 记录 Client 到 Server 的调用。Native Tool 只有 `Tool` Span。

## 4. 示例

Trace
|
+-- LLM
|
+-- Tool
    +-- MCP
|
+-- RAG
|
+-- LLM
|
+-- Final Answer

## 5. 存储

V1 可以使用 PostgreSQL。

V1 不要求 OpenTelemetry。

以后可以作为扩展加入 OpenTelemetry。

V1 Trace 脱敏只处理两类内容：配置为 secret 的字段及 HTTP `Authorization` 值替换为 `[REDACTED]`；`input`、`output`、`error` 采用统一可配置长度截断。V1 不要求通用 PII 检测或复杂脱敏规则。

## 6. API

至少：
- GET /traces/{traceId}
- GET /runs/{runId}

## 7. 验收标准

- [ ] 每次执行生成 trace_id
- [ ] 每个主要执行步骤产生 Trace Event
- [ ] 记录 Latency
- [ ] 记录 Status
- [ ] 记录 Error
- [ ] 可以通过 run_id 查询 Trace
- [ ] 前端可以显示执行顺序
