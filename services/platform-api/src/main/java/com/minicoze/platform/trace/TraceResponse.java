package com.minicoze.platform.trace;

import com.minicoze.platform.run.RunStatus;
import java.time.Instant;
import java.util.*;
public record TraceResponse(UUID trace_id, UUID run_id, UUID agent_id, RunStatus status, Instant start_time, Instant end_time, Long total_latency_ms, List<Span> spans) {
    public record Span(UUID span_id, UUID parent_span_id, String node_type, String name, String status, String input, String output, String error, Instant start_time, Instant end_time, Long latency_ms) {}
    public static TraceResponse from(TraceEntity trace,List<TraceSpanEntity> spans){return new TraceResponse(trace.getId(),trace.getRunId(),trace.getAgentId(),trace.getStatus(),trace.getStartedAt(),trace.getEndedAt(),trace.getTotalLatencyMs(),spans.stream().map(s->new Span(s.getId(),s.getParentSpanId(),s.getSpanType(),s.getName(),s.getStatus(),s.getInput(),s.getOutput(),s.getError(),s.getStartedAt(),s.getEndedAt(),s.getLatencyMs())).toList());}
}
