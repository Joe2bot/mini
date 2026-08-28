package com.minicoze.platform.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentResponse(UUID id, String name, String description, String systemPrompt, String modelProvider,
                            String modelName, Double temperature, Integer maxTokens, Instant createdAt, Instant updatedAt) {
    public static AgentResponse from(AgentEntity a) { return new AgentResponse(a.getId(), a.getName(), a.getDescription(), a.getSystemPrompt(), a.getModelProvider(), a.getModelName(), a.getTemperature(), a.getMaxTokens(), a.getCreatedAt(), a.getUpdatedAt()); }
}
