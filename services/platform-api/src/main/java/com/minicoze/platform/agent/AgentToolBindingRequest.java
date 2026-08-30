package com.minicoze.platform.agent;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AgentToolBindingRequest(@NotNull List<UUID> toolIds) {}
