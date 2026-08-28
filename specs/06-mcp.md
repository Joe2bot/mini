# MCP 规格

## 1. 目的

通过 Model Context Protocol 集成外部 Tools。

MCP 被视为工具集成协议，而不是 Agent Runtime。

## 2. V1 要求

- 实现 MCP Client 集成。
- 接入至少一个第三方 MCP Server。
- 自己实现至少一个简单 MCP Server，并提供至少一个 Tool。
- 能从 MCP Server 发现 Tools。
- 将 MCP Tools 转换为平台内部统一的 Tool 表示。
- V1 只支持 `stdio` Transport；第三方和自建 MCP Server 均必须使用该 Transport。

## 3. 内部 Tool 抽象

无论 Tool 来自 Native 还是 MCP，Runtime 都应该使用统一结构：

- tool_name
- description
- input_schema
- source_type: native | mcp
- source_id
- executor

Tool Registry 使用全局唯一的 `tool_name`。发现 MCP Tool 时，Runtime 生成规范名 `mcp__{server_id_normalized}__{remote_tool_name_normalized}`，并保存原始远端名称供 MCP Client 调用。Agent 和 LLM 只使用规范名；若规范化后仍冲突，连接失败并返回 Configuration Error。

这样 Agent 不需要关心 Tool 的具体来源。

## 4. MCP 流程

Agent
 -> LLM Tool Call
 -> Runtime Tool Registry
 -> MCP Client
 -> MCP Server
 -> 外部系统
 -> MCP Result
 -> Runtime
 -> LLM

## 5. 配置

MCP Server 至少包含：
- id
- name
- transport（V1 固定为 `stdio`）
- configuration（stdio command、arguments、environment references）
- enabled

Secret 不允许硬编码到源码。

## 6. 验收标准

- [ ] 添加 MCP Server
- [ ] 连接 MCP Server
- [ ] 发现 Tools
- [ ] 注册 Tools
- [ ] Agent 可以调用 MCP Tool
- [ ] MCP Result 返回 Agent
- [ ] MCP 出现在 Trace
- [ ] MCP 失败可以被正确处理
- [ ] 同名 MCP Tool 不会覆盖 Native Tool 或其他 MCP Server 的 Tool
