package com.minicoze.platform.run;
import jakarta.validation.constraints.NotBlank;
public record CreateRunRequest(@NotBlank String input) {}
