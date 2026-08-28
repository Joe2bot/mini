# Agent 规格

## 1. 目的

允许用户创建和配置一个单 Agent。

## 2. Agent 字段

必填：
- id
- name
- description
- system_prompt
- model_provider
- model_name

可选：
- temperature
- max_tokens
- tools
- knowledge_bases

## 3. Agent 行为

执行 Agent 时：

1. Spring Boot 加载 Agent 配置，Runtime 接收该配置的不可变快照。
2. 构建 System Prompt。
3. 加载允许使用的 Tools。
4. 将 Agent 已绑定的 Knowledge Base 暴露为 `knowledge_search` Tool；不自动执行 Retriever。
5. 向 LLM 发送用户输入。
6. 如果 LLM 返回 Tool Call，则执行工具。
7. 将 Tool Result 加入上下文，继续 Agent Loop。
8. 直到得到最终回答或触发停止条件。
9. 每个执行步骤产生 Trace。

## 4. 配置规则

- 一个 Agent 必须有一个默认 Model。
- Tool 权限必须显式配置。
- Knowledge Base 权限必须显式配置。
- 每个已绑定的 Knowledge Base 只能由 `knowledge_search` Tool 在其授权范围内检索。
- Model 配置无效时，应在执行前失败。
- Agent 不得访问未配置的 Tool。

## 5. 验收标准

- [ ] 创建 Agent
- [ ] 修改 Agent
- [ ] 删除 Agent
- [ ] 配置 System Prompt
- [ ] 配置 Model
- [ ] 绑定 Tools
- [ ] 绑定 Knowledge Base
- [ ] 可通过 Knowledge Search Tool 检索已绑定 Knowledge Base
- [ ] 执行 Agent
- [ ] 获取最终回答
- [ ] 产生 Trace
