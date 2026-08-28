from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, Field


class Limits(BaseModel):
    max_iterations: int = 10
    tool_timeout_seconds: int = 30
    total_execution_timeout_seconds: int = 300


class RuntimeStartRequest(BaseModel):
    run_id: UUID
    trace_id: UUID
    target_type: Literal["agent"]
    target_id: UUID
    workflow_version: int | None = None
    input: str
    configuration_snapshot: dict[str, Any]
    limits: Limits = Field(default_factory=Limits)


class RuntimeEvent(BaseModel):
    event_id: int
    event_type: str
    run_id: UUID
    trace_id: UUID
    timestamp: datetime
    data: dict[str, Any]

    @classmethod
    def create(cls, event_id: int, event_type: str, run_id: UUID, trace_id: UUID, data: dict[str, Any]) -> "RuntimeEvent":
        return cls(event_id=event_id, event_type=event_type, run_id=run_id, trace_id=trace_id, timestamp=datetime.now(timezone.utc), data=data)
