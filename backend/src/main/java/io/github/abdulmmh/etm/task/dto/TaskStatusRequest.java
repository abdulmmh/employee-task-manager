package io.github.abdulmmh.etm.task.dto;

import io.github.abdulmmh.etm.task.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {}
