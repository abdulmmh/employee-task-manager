package io.github.abdulmmh.etm.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.abdulmmh.etm.user.enums.Role;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardResponse(
    Role role, Long totalUsers, Long activeUsers, Long teamSize,
    long totalTasks, long completedTasks, StatusCounts tasksByStatus
) {
    public record StatusCounts(long pending, long inProgress, long done) {}
}
