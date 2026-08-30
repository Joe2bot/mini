package com.minicoze.platform.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ToolRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank String inputSchema,
        @NotNull ToolSourceType sourceType,
        @NotBlank @Size(max = 160) String sourceId,
        boolean enabled) {}
