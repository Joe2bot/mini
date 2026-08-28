# Mini Coze V1 — 产品规格

## 1. 项目目的

构建一个轻量级、开源的 Agent Platform，作为 Agent Developer 面试/作品集项目。

系统需要体现以下核心能力：
- Agent
- Tool Calling（工具调用）
- Workflow（工作流）
- Agent Runtime（Agent 运行时）
- RAG
- MCP
- Observability / Trace（可观测性 / 执行轨迹）

目标不是复制完整的 Dify 或 Coze，而是构建一个规模较小、架构清晰、能够真正执行 Agent 任务的平台。

## 2. V1 范围

### 必须实现
1. Agent
2. Tool Calling
3. Workflow
4. Runtime
5. RAG
6. MCP
7. Trace

### V1 不实现
- 多租户
- 计费 / 支付
- 企业级 IAM
- 完整模型市场
- 复杂企业监控
- 生产级分布式部署
- 高级协作
- Multi-Agent 作为必选功能
- Fine-tuning
- A/B Testing

## 3. 目标用户

开发者可以：
- 创建 Agent
- 配置模型、Prompt 和 Tools
- 创建 Workflow
- 绑定知识库
- 连接 MCP Tools
- 执行 Agent / Workflow
- 查看执行 Trace

## 4. 推荐技术栈

### 前端
- React
- TypeScript
- React Flow：Workflow 画布

### 平台后端
- Spring Boot
- REST API
- PostgreSQL

### Agent Runtime
- Python
- LangGraph：Graph / State 执行
- LangChain：LLM / RAG / Tool 等组件集成

### RAG
- Qdrant
- Embedding Model
- LangChain Retrieval 组件

### MCP
- MCP Client
- 至少一个第三方 MCP Server
- 至少一个自己实现的简单 MCP Server

### 基础设施
- Docker Compose

## 5. 总体架构

React
  |
  v
Spring Boot API
  |
  +-- Agent 配置
  +-- Workflow 配置
  +-- 知识库元数据
  +-- Tool / MCP 配置
  +-- Trace 查询 API
  |
  +-- HTTP：创建并启动 Runtime Run
  +-- SSE：接收 Runtime 事件并转发给前端
  |
  v
Python Agent Runtime
  |
  +-- Agent 执行
  +-- Workflow 执行
  +-- Tool Registry
  +-- Context / State
  +-- Trace 收集
  |
  +--> LangGraph
  +--> LangChain
  +--> LLM
  +--> Qdrant
  +--> MCP Client
  |
  v
外部 Tools / MCP Servers

## 6. 架构原则

1. 配置和执行必须分离。
2. Spring Boot 负责平台级配置和 API。
3. Python 负责 Agent / LLM / RAG 执行。
4. LangGraph 负责 Graph / State / Workflow 执行，不把它视为整个 Agent Platform Runtime。
5. LangChain 作为组件和集成层，而不是整个 Agent Platform。
6. MCP 是工具集成协议，不是 Agent Runtime。
7. Trace 必须在执行过程中产生，而不是执行结束后再推算。
8. 所有核心功能必须有最基本的自动化测试。
9. V1 不为了“看起来完整”而引入不必要的框架。
10. Spring Boot 是业务配置数据（Agent、Workflow、Knowledge Base、MCP Server）的唯一写入口；Runtime 不直接读写 PostgreSQL。
11. Spring Boot 与 Runtime 使用 HTTP 启动 Run；Runtime 以 SSE 输出执行事件，Spring Boot 持久化 Run/Trace 并向前端转发 SSE。
12. Agent 和 Workflow 使用同一份 V1 State Schema；执行元数据属于 Execution Context，不混入业务 State。

## 7. 核心端到端场景

用户创建一个 Agent：

- System Prompt：你是一个技术研究助手。
- Model：配置好的 LLM
- Tools：Search Tool + MCP Tool
- Knowledge Base：技术文档

用户输入：

“解释这个项目是如何工作的，并在需要时使用可用工具。”

预期：

User
 -> Spring Boot 创建异步 Run
 -> Agent Runtime
 -> LLM
 -> Tool Call
 -> Tool / MCP 执行
 -> Knowledge Search Tool（可选）检索 RAG
 -> LLM
 -> Final Answer

整个执行过程必须可以在 Trace 中查看。

## 8. V1 完成标准

- 可以创建并执行 Agent。
- LLM 输出和执行进度可通过 SSE 正常流式查看。
- LLM 可以产生结构化 Tool Call。
- Runtime 可以执行 Tool，并把结果返回给 LLM。
- 可以定义并通过 LangGraph 执行 Workflow。
- 文档可以被解析、切分、Embedding，并存入 Qdrant。
- MCP Server 可以提供 Tool，Agent 可以调用该 Tool。
- 每次执行都会产生 Trace，包括步骤、状态和耗时。
- 前端可以创建/配置 Agent 和 Workflow，并查看执行结果。
- 可以通过 Docker Compose 或明确的本地命令启动整个项目。
