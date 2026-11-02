package io.github.abdulmmh.etm.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
    Long id, String title, String description, TaskStatus status, TaskPriority priority,
    LocalDate deadline, Long assignedTo, String assigneeName, Long assignedBy,
    String assignerName, LocalDateTime createdAt, LocalDateTime updatedAt
) {}
