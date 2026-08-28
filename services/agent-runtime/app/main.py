from __future__ import annotations

import json
from uuid import UUID

from fastapi import FastAPI, Header, HTTPException, Response
from fastapi.responses import StreamingResponse

from app.llm import create_llm_adapter
from app.runtime import RunManager
from app.schemas import RuntimeStartRequest

app = FastAPI(title="Mini Coze Agent Runtime", docs_url=None, redoc_url=None)
manager = RunManager(create_llm_adapter())


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/v1/runs", status_code=202)
async def create_run(request: RuntimeStartRequest) -> Response:
    if request.target_type != "agent":
        raise HTTPException(status_code=400, detail="Only agent runs are supported in this vertical slice")
    await manager.start(request)
    return Response(status_code=202)


@app.get("/internal/v1/runs/{run_id}/events")
async def events(run_id: UUID, last_event_id: str | None = Header(default=None)) -> StreamingResponse:
    if run_id not in manager.sessions:
        raise HTTPException(status_code=404, detail="Run not found")
    try:
        last = int(last_event_id or 0)
    except ValueError:
        last = 0
    async def generate():
        async for event in manager.stream_events(run_id, last):
            yield f"id: {event.event_id}\nevent: {event.event_type}\ndata: {json.dumps(event.model_dump(mode='json'), ensure_ascii=False)}\n\n"
    return StreamingResponse(generate(), media_type="text/event-stream")
