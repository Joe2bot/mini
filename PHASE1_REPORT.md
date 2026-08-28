# Mini Coze V1 — Phase 1 Vertical Slice 交付报告

## 1. 已实现功能

- React Agent Test UI：Agent 列表、创建/编辑/删除、输入消息、Run 状态、流式输出、最终结果与 Trace 展示。
- Spring Boot Agent CRUD 与异步 Run API。
- Run 状态：`queued → running → completed`，以及 `queued/running → failed`。
- 创建 Run 时生成 `run_id`、`trace_id` 和不可变 configuration snapshot。
- Spring Boot 通过内部 HTTP 启动 Python Runtime，并订阅 Runtime SSE。
- Python Runtime 支持 Fake LLM 和 OpenAI-compatible LLM Adapter 的流式最终回答。
- Runtime 不访问 PostgreSQL；Spring Boot 持久化 Run、Trace、LLM Span 与 SSE event。
- 公开 Run SSE 重放与 Trace 查询。
- Docker Compose、Dockerfile、README 和自动化测试工程。

## 2. 新增/修改文件

主要目录与文件：

- `README.md`
- `infra/docker-compose.yml`
- `apps/web/`：React + TypeScript 前端
- `services/platform-api/`：Spring Boot、JPA、REST/SSE 与测试
- `services/agent-runtime/`：FastAPI、LLM Adapter 与测试

Spec 与 Design 未修改。

## 3. 当前项目目录结构

```text
mini-coze/
├── apps/web/                       # React + TypeScript
├── services/
│   ├── platform-api/                # Spring Boot + JPA + REST/SSE
│   └── agent-runtime/               # FastAPI + LLM Adapter
├── infra/docker-compose.yml
├── specs/
├── DESIGN.md
└── README.md
```

## 4. API 列表

```text
POST   /api/agents
GET    /api/agents
GET    /api/agents/{id}
PUT    /api/agents/{id}
DELETE /api/agents/{id}

POST   /api/agents/{id}/runs         # 202 Accepted
GET    /api/runs/{runId}
GET    /api/runs/{runId}/events      # SSE
GET    /api/traces/{traceId}

POST   /internal/v1/runs             # Runtime 内部接口
GET    /internal/v1/runs/{runId}/events
```

## 5. Runtime 与 Spring Boot 通信方式

```text
Browser → Spring Boot POST Run
Spring Boot → PostgreSQL: queued Run + Trace + snapshot
Spring Boot → Runtime: POST /internal/v1/runs
Runtime → Spring Boot: SSE Runtime events
Spring Boot → PostgreSQL: Run / Trace / Span / event
Spring Boot → Browser: SSE replay + live events
```

Runtime Snapshot 包含 Agent 的 System Prompt、模型配置、温度、最大 Token 和用户输入。

## 6. SSE Event Schema

事件类型：

```text
run.started
trace.span.started
llm.delta
trace.span.completed
run.completed
run.failed
```

Runtime event payload：

```json
{
  "event_id": 3,
  "event_type": "llm.delta",
  "run_id": "uuid",
  "trace_id": "uuid",
  "timestamp": "2026-08-29T03:00:00Z",
  "data": {
    "delta": "streamed text"
  }
}
```

Spring Boot 持久化每个 event，并以其 `run_events.id` 作为浏览器 SSE 的 `id`，支持终态 Run 的历史事件重放。

## 7. Trace 数据结构

```text
Trace
└── LLM Span
```

Trace 保存：

- `trace_id`
- `run_id`
- `agent_id`
- `status`
- `started_at` / `ended_at`
- `total_latency_ms`
- LLM Span 的输入、输出、状态、耗时与错误

数据库实体包括 `runs`、`traces`、`trace_spans`、`run_events` 与 `agents`。

## 8. 测试结果

- Python Unit Test：`2 passed`
  - Fake LLM 流式输出
  - Runtime LLM Trace 与完成事件

- Spring Boot Unit / Integration Test：`4 passed`
  - Agent Service
  - Run 状态转换
  - Run API 返回 202、Trace 持久化、SSE replay
  - Runtime event 到 Run/Trace 的映射

- E2E Smoke Test：`1 passed`
  - 创建 Agent
  - 创建 Run
  - Spring Boot HTTP 启动真实 Python Runtime
  - Fake LLM 流式输出
  - Runtime SSE 回流 Spring Boot
  - 查询 completed Run、公开 SSE 与 LLM Trace

- 前端：`npm run build` 通过。

## 9. Definition of Done

| 项目 | 状态 |
|---|---|
| Agent CRUD 正常 | 通过 |
| Run API 返回 202 | 通过 |
| configuration snapshot 正确生成 | 通过 |
| Spring Boot 启动 Runtime Run | 通过 |
| Runtime 执行 LLM | 通过 |
| SSE 实时传输事件 | 通过 |
| 前端显示流式结果 | 通过（已实现并构建通过） |
| Run 变为 completed/failed | 通过 |
| Trace 可查询 | 通过 |
| Unit Tests | 通过 |
| Integration Tests | 通过 |
| E2E Smoke Test | 通过 |
| 未实现 Phase 2 功能 | 通过 |
| 未修改冻结架构 | 通过 |

## 10. 当前已知问题

- 当前开发环境的 Docker daemon 未启动，因此未实际执行 `docker compose up`；真实 E2E 已通过本地 Python Runtime 与 Spring Boot 验证。
- 本机 Netty 会输出 macOS DNS native resolver 缺失警告；本地 HTTP/SSE E2E 正常通过，不影响功能。
- OpenAI-compatible Adapter 已实现，但自动化测试只使用 Fake LLM，不依赖真实 API Key。
- Maven 在当前环境需显式使用 Java 17；项目仍以 Java 17 为目标。
