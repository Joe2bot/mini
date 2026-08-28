# Agent Runtime 规格

## 1. 目的

提供 Agent 和 Workflow 的平台级执行层。

Runtime 不是 LangGraph 的替代品。LangGraph 负责 Graph / State 执行，Runtime 负责平台级执行生命周期。

## 2. Runtime 职责

Runtime 必须：
- 通过内部 HTTP 接收 Spring Boot 发起的执行请求和不可变配置快照
- 创建执行上下文
- 使用 Spring Boot 提供的 run_id / trace_id
- 初始化 State
- 提供允许的 Tools
- 在需要时调用 LangGraph
- 执行 Native Tool 和 MCP Tool
- 处理 Tool Result
- 执行 Timeout 和 Iteration 限制
- 在执行期间产生 Run、Trace 与输出事件，并通过 SSE 发送给 Spring Boot
- 返回最终结果事件

Spring Boot 负责创建 Run/Trace 元数据并持久化 Runtime SSE 事件；Runtime 不直接写入 PostgreSQL，也不修改业务配置。

## 3. Execution Context

至少包含：

- run_id
- trace_id
- agent_id 或 workflow_id
- user input
- current state
- available tools
- execution metadata

Execution Context 是 Runtime 私有的运行元数据。`current state` 必须遵循下述统一 V1 State Schema。

## 4. 统一 V1 State Schema

Agent 和 Workflow 均使用以下 JSON 对象作为 State；字段不存在时使用空值或空集合。`variables` 用于 Workflow Node 间的确定性数据传递。

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

- `messages`：Agent 或 LLM Node 的对话消息。
- `tool_results`：已完成 Tool Call 的结构化结果。
- `rag_context`：RAG 检索到、可注入 LLM 的文档片段。
- `output`：最终输出；执行完成前可以为 `null`。
- `condition_result`：Condition Node 的布尔结果；其他场景为 `null`。
- `variables`：JSON 对象，供 Workflow Node 使用；不得覆盖保留字段。

## 5. Agent Loop

V1：

1. 准备 Context。
2. 调用 LLM。
3. 如果是 Final Response，则结束。
4. 如果是 Tool Call，则校验并执行。
5. 将 Tool Result 加入 State / Context。
6. 再次调用 LLM。
7. 直到最终回答、错误、Timeout 或达到最大 Iteration。

## 6. 限制

默认：
- max_iterations = 10
- tool_timeout = 30 秒
- total_execution_timeout = 5 分钟

这些值必须可配置，不允许散落在代码中硬编码。

## 7. 错误处理

至少区分：
- Configuration Error
- LLM Error
- Tool Error
- RAG Error
- MCP Error
- Timeout
- Max Iteration Stop

错误必须写入 Trace。

## 8. 验收标准

- [ ] Agent Execution API 正常
- [ ] Workflow Execution API 正常
- [ ] State 可以跨多个步骤保存
- [ ] Tool Call 可以执行
- [ ] MCP Tool 可以执行
- [ ] Timeout 生效
- [ ] Max Iteration 生效
- [ ] 错误返回结构稳定
- [ ] 每次 Run 都有 run_id 和 trace_id
- [ ] Runtime 通过 SSE 输出执行事件，Spring Boot 可持久化并转发
- [ ] Agent 和 Workflow 均遵循统一 State Schema
