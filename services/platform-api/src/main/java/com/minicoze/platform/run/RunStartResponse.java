package com.minicoze.platform.run;
import java.util.UUID;
public record RunStartResponse(UUID run_id, UUID trace_id, RunStatus status) {}
