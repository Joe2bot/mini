# Frontend 规格

## 1. 必须页面

### Agent Builder
- Agent Name
- Description
- System Prompt
- Model Selection
- Tool Selection
- Knowledge Base Selection
- Run / Test 区域
- 通过 SSE 显示运行状态、输出增量和最终结果

### Workflow Builder
使用 React Flow。

必须支持：
- Node Palette
- Canvas
- Node 连接
- Node Configuration 编辑
- 保存 Workflow
- 执行 Workflow
- 显示 Run 固定的 Workflow version

### Knowledge Base
- Knowledge Base 列表
- 创建 Knowledge Base
- 上传文档
- 显示 Ingestion 状态

### Trace Viewer
显示：
- Run Status
- Total Latency
- Execution Steps
- Step 类型/名称
- Latency
- Error
- 可展开查看 Input / Output

## 2. UI 优先级

功能 > 视觉效果。

V1 不需要复制完整 Dify / Coze UI。

## 3. 验收标准

- [ ] 可以配置 Agent
- [ ] 可以运行 Agent
- [ ] 可以创建和运行 Workflow
- [ ] 可以上传文档
- [ ] 可以配置并使用 MCP
- [ ] 可以查看 Trace
- [ ] 可以通过 SSE 查看运行进度与流式输出
