package com.minicoze.platform.agent;

import jakarta.validation.constraints.*;

public record AgentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank String systemPrompt,
        @NotBlank String modelProvider,
        @NotBlank String modelName,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Positive Integer maxTokens) {}
