package io.github.abdulmmh.etm.report;

import io.github.abdulmmh.etm.task.TaskPriority;
import io.github.abdulmmh.etm.task.TaskStatus;
import java.time.LocalDate;

public record TaskReportRow(Long id, String title, String assigneeName,
        String assignerName, TaskStatus status, TaskPriority priority, LocalDate deadline) {}
