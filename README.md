# Mini Coze V1

第一个 Vertical Slice：Agent CRUD、异步 Agent Run、Fake/OpenAI-compatible LLM Adapter、Runtime 到 Platform 到 Frontend 的 SSE，以及最小 Trace。

## 本地启动

运行依赖服务：

```bash
docker compose -f infra/docker-compose.yml up --build
```

打开 `http://localhost:5173`。默认使用 `LLM_PROVIDER=fake`，无需 API Key。真实 OpenAI-compatible 服务需设置 `LLM_PROVIDER=openai_compatible`、`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`。

## 测试

```bash
cd services/platform-api && mvn test
cd services/agent-runtime && uv run --with-requirements requirements.txt pytest
cd apps/web && npm install && npm test
```

本阶段刻意不包含 Tool、RAG、MCP、Workflow 或 Multi-Agent。
