package com.minicoze.platform.run;
import java.time.Instant;
import java.util.UUID;
public record RunResponse(UUID run_id, UUID trace_id, UUID agent_id, RunStatus status, String input, String output, Instant started_at, Instant ended_at, String error_code, String error_message) {
    public static RunResponse from(RunEntity r){return new RunResponse(r.getId(),r.getTraceId(),r.getAgentId(),r.getStatus(),r.getInput(),r.getOutput(),r.getStartedAt(),r.getEndedAt(),r.getErrorCode(),r.getErrorMessage());}
}
