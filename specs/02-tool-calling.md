# Tool Calling 规格

## 1. 目的

允许 LLM 通过结构化 Tool Call 请求外部能力。

## 2. Tool 模型

每个 Tool 必须提供：

- name
- description
- input_schema（标准 JSON Schema）
- executor
- enabled / disabled

示例：

{
  "name": "calculator",
  "description": "计算数学表达式",
  "input_schema": {
    "type": "object",
    "properties": {
      "expression": { "type": "string" }
    },
    "required": ["expression"],
    "additionalProperties": false
  }
}

## 3. 执行流程

User
 -> LLM
 -> 结构化 Tool Call
 -> Runtime Tool Registry
 -> 参数校验
 -> Tool 执行
 -> Tool Result
 -> LLM
 -> Final Answer

## 4. Tool 来源

V1 支持：
- Native / Local Tools
- MCP Tools

至少演示三个 Native Tool：
- calculator
- HTTP/API 类型 Tool
- 简单数据库或 Utility Tool

`knowledge_search` 是 Runtime 提供的 Native Tool，用于 Agent 对已绑定 Knowledge Base 的显式检索。其输入为标准 JSON Schema Object，包含必填 `knowledge_base_id` 和 `query`；Runtime 必须校验 `knowledge_base_id` 属于该 Agent 的授权集合。

## 5. 安全与校验

- Tool 名称必须存在于 Agent 的允许 Tool 集合。
- 参数必须根据标准 JSON Schema 校验。
- Tool 执行必须有 Timeout。
- Tool 错误必须以结构化错误返回给 Agent。
- Tool Call 必须记录到 Trace。
- Calculator 仅支持受限数学表达式，不得使用通用代码执行。
- V1 的 HTTP/API Tool 仅允许调用平台预配置的公开 HTTP `GET` 端点；不接受模型或用户提供的 URL、请求头或请求体。

## 6. 验收标准

- [ ] LLM 可以请求 Tool。
- [ ] Runtime 校验 Tool 名称。
- [ ] Runtime 校验参数。
- [ ] Runtime 执行 Tool。
- [ ] Tool Result 返回给 LLM。
- [ ] 支持连续多次 Tool Call。
- [ ] Tool 失败不会导致整个服务直接崩溃。
- [ ] Tool Call 出现在 Trace。
