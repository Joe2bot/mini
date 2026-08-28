# Testing 规格

## 1. 测试层级

### Unit Test
- Tool Validation
- Runtime Stop Conditions
- Workflow Condition
- RAG Transformation
- Trace Event Creation
- 标准 JSON Schema Tool 参数校验
- MCP Tool 规范命名与冲突处理
- 统一 State Schema 的 Node 更新

### Integration Test
- Agent -> LLM -> Tool
- Agent -> MCP Tool
- Workflow -> LLM -> Tool
- RAG -> Qdrant -> LLM
- Trace Persistence
- Spring Boot 与 Runtime 的 HTTP 启动、SSE 转发及事件持久化

### End-to-End Smoke Test

完整执行：
- 创建 Agent
- 执行 Agent
- 调用 Tool
- 获取最终回答
- 查询 Trace
- 通过 SSE 接收运行状态和最终输出

## 2. 验收原则

功能不能只以“代码能编译”为完成标准。

每个功能必须包含：
1. 实现
2. 测试
3. Acceptance Criteria 验证

## 3. Definition of Done

- 完成代码
- 测试通过
- 遵循 API Contract
- 没有加入无关功能
- 包含必要错误处理
- 执行类功能包含 Trace
- 用户可见行为发生变化时更新 README
