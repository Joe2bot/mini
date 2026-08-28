# Workflow 规格

## 1. 目的

提供可视化、可执行的 Workflow，用于确定性或半确定性的 Agent 流程。

## 2. V1 节点

必须支持：
- Start
- End
- LLM
- Tool
- RAG
- Condition

可选：
- HTTP Request

## 3. Workflow 数据结构

Workflow 保存为版本化 JSON，包含：
- workflow id
- version
- nodes
- edges
- node configuration

版本规则：
- 每次保存 Workflow Definition 创建一个不可变的新 `version`。
- Run 创建时记录并固定 `workflow_version`，执行和 Trace 查询均使用该版本，不受后续保存影响。
- 未指定版本的 Run 使用创建时的当前版本；指定版本必须属于该 Workflow。

Node：
- id
- type
- configuration

Edge：
- source
- target
- 可选 condition metadata

所有 Node 从统一 V1 State Schema 读取并只更新其允许的字段；Node 配置必须声明输入来源与输出字段。

## 4. 执行

Spring Boot 在创建 Run 时确定 Workflow Definition 的不可变版本快照；Runtime 接收该快照并将其转换为 LangGraph Graph。

LangGraph 负责：
- Graph State
- Node Execution
- Edge
- Conditional Routing
- Graph Execution

平台 Runtime 负责：
- 使用配置快照
- Session / Run 元数据
- Tool Registry
- Trace
- Error / Timeout Policy
- 整体执行生命周期

Workflow 的 RAG Node 是 Workflow 使用知识库的唯一方式，且必须引用一个 Knowledge Base。

## 5. Condition

V1 至少支持：
- Boolean Result
- Branch A / Branch B

Condition 仅支持受限的结构化规则：`equals`、`not_equals`、`exists`、`truthy`。Condition 基于 State 中指定字段计算 Boolean Result，并路由至 Branch A 或 Branch B。

V1 不支持 LLM 分类、脚本或通用表达式语言。

## 6. 验收标准

- [ ] 创建 Workflow
- [ ] 添加 Node
- [ ] 连接 Node
- [ ] 保存 Workflow JSON
- [ ] 加载 Workflow
- [ ] 执行 Workflow
- [ ] LLM Node 工作
- [ ] Tool Node 工作
- [ ] RAG Node 工作
- [ ] Condition 正确分支
- [ ] End 返回最终结果
- [ ] Workflow 执行出现在 Trace
- [ ] Run 固定执行创建时选择的 Workflow version
