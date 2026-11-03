package io.github.abdulmmh.etm.task.repository;

import io.github.abdulmmh.etm.task.enums.TaskStatus;

/** Projection of one GROUP BY status result; no task entities are loaded. */
public interface TaskStatusCount {
    TaskStatus getStatus();
    long getTotal();
}
