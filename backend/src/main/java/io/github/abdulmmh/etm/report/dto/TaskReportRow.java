package io.github.abdulmmh.etm.report.dto;

import io.github.abdulmmh.etm.task.enums.TaskPriority;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import java.time.LocalDate;

public record TaskReportRow(Long id, String title, String assigneeName,
        String assignerName, TaskStatus status, TaskPriority priority, LocalDate deadline) {}
