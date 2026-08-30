# Mini Coze V1

已完成的 Vertical Slice：Agent CRUD、Tool CRUD 与 Agent Tool allow-list、异步 Agent Run、Fake/OpenAI-compatible LLM Adapter、受限 Native Tool Calling、Runtime 到 Platform 到 Frontend 的 SSE，以及最小 Trace。

## 本地启动

运行依赖服务：

```bash
docker compose -f infra/docker-compose.yml up --build
```

打开 `http://localhost:5173`。默认使用 `LLM_PROVIDER=fake`，无需 API Key。真实 OpenAI-compatible 服务需设置 `LLM_PROVIDER=openai_compatible`、`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`。

Native Tool 包括 `calculator`、`text_length`、`current_timestamp` 和由配置定义的 `http_get`。`http_get` 的 `sourceId` 是 Runtime 环境变量引用，例如 `WEATHER_API_ENDPOINT`；模型只传 JSON Schema 允许的业务参数，不能传入 URL、Host、请求头或 Secret。Runtime 会对解析后的 endpoint 执行协议、Host allow-list、DNS/IP SSRF、重定向、超时与响应大小限制检查。

## 测试

```bash
cd services/platform-api && mvn test
cd services/agent-runtime && uv run --with-requirements requirements.txt pytest
cd apps/web && npm install && npm run build
```

本阶段刻意不包含 RAG、MCP 执行、Workflow 或 Multi-Agent。
