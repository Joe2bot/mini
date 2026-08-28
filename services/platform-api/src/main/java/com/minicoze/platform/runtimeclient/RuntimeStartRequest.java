package com.minicoze.platform.runtimeclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record RuntimeStartRequest(@JsonProperty("run_id") UUID runId, @JsonProperty("trace_id") UUID traceId,
                                  @JsonProperty("target_type") String targetType, @JsonProperty("target_id") UUID targetId,
                                  @JsonProperty("workflow_version") Integer workflowVersion, String input,
                                  @JsonProperty("configuration_snapshot") JsonNode configurationSnapshot, Limits limits) {
    public record Limits(@JsonProperty("max_iterations") int maxIterations, @JsonProperty("tool_timeout_seconds") int toolTimeoutSeconds, @JsonProperty("total_execution_timeout_seconds") int totalExecutionTimeoutSeconds) {}
}
