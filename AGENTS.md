# AGENTS.md — Mini Coze 开发规则

## 项目目标

构建一个轻量级、开源的 Agent Platform，作为 Agent Developer 面试/作品集项目。

实现功能前，必须先阅读 `/specs/00-overview.md` 和对应模块 Spec。

## 架构

### Frontend
- React + TypeScript
- React Flow

### Platform Backend
- Spring Boot
- REST API
- PostgreSQL

### Agent Runtime
- Python
- LangGraph
- LangChain

### RAG
- Qdrant

### MCP
- MCP Client / MCP Server

## 开发规则

1. 遵循 `/specs` 中的规格。
2. 不得擅自增加 Scope 外功能。
3. 未经批准不得更换整体架构。
4. 不要重复实现框架已经提供的能力。
5. Spring Boot 负责平台配置和 API。
6. Python 负责 Agent / LLM / RAG 执行。
7. LangGraph 负责 Graph / State 执行。
8. LangChain 作为组件/集成层使用。
9. Native Tools 和 MCP Tools 必须通过统一 Tool Registry 抽象。
10. 所有执行类功能必须产生 Trace。
11. 每个功能必须有适当测试。
12. API Key / Secret 不得硬编码。
13. API 不得向客户端暴露原始 Stack Trace。
14. 优先简单实现，避免过早企业级抽象。
15. 一个任务不要无必要地修改多个无关模块。

## Codex 工作流程

每次任务：

1. 阅读相关 Spec。
2. 检查现有代码。
3. 简要说明实现计划。
4. 实现最小完整变更。
5. 运行相关测试。
6. 修复失败。
7. 对照 Acceptance Criteria 验证。
8. 汇总修改文件和测试结果。

如果实现需要修改架构或 API Contract，不允许静默修改，必须先请求批准。

## V1 不做

- 计费
- 多租户
- 企业级 IAM
- 完整模型市场
- 复杂分布式部署
- Multi-Agent 作为必选功能
- Fine-tuning
- 高级 Analytics

## 核心术语

Agent：
基于模型进行任务执行，并能够决定是否调用可用 Tools。

Tool Calling：
LLM 以结构化形式请求执行 Tool。

Workflow：
由 Node 和 Edge 组成的流程图。

LangGraph：
Graph / State 执行和编排层。

Platform Runtime：
平台级 Agent / Workflow 执行生命周期。

RAG：
文档检索和 Context 注入。

MCP：
用于标准化外部 Tool / Resource 集成的 Model Context Protocol。

Trace：
一次 Agent / Workflow 执行及其步骤的记录。
