package com.minicoze.platform.tool;

import java.time.Instant;
import java.util.UUID;

public record ToolResponse(UUID id, String name, String description, String inputSchema, ToolSourceType sourceType,
                           String sourceId, boolean enabled, Instant createdAt, Instant updatedAt) {
    public static ToolResponse from(ToolEntity t) { return new ToolResponse(t.getId(), t.getToolName(), t.getDescription(), t.getInputSchema(), t.getSourceType(), t.getSourceId(), t.isEnabled(), t.getCreatedAt(), t.getUpdatedAt()); }
}
