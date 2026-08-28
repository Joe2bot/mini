package com.minicoze.platform.runtimeclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record RuntimeEvent(@JsonProperty("event_id") long eventId, @JsonProperty("event_type") String eventType,
                           @JsonProperty("run_id") UUID runId, @JsonProperty("trace_id") UUID traceId,
                           Instant timestamp, JsonNode data) {}
