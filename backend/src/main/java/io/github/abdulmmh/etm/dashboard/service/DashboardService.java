package io.github.abdulmmh.etm.dashboard.service;

import io.github.abdulmmh.etm.dashboard.dto.DashboardResponse;
import io.github.abdulmmh.etm.task.repository.TaskRepository;
import io.github.abdulmmh.etm.task.repository.TaskStatusCount;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    @Transactional(readOnly = true)
    public DashboardResponse stats(long actorId) {
        var actor = userRepository.findById(actorId).filter(user -> user.isActive())
            .orElseThrow(() -> new AccessDeniedException("An active account is required."));
        Role role = actor.getRole();
        Long totalUsers = null;
        Long activeUsers = null;
        Long teamSize = null;
        List<TaskStatusCount> rows;
        switch (role) {
            case ADMIN -> {
                totalUsers = userRepository.count();
                activeUsers = userRepository.countByActiveTrue();
                rows = taskRepository.countAllByStatus();
            }
            case MANAGER -> {
                teamSize = userRepository.countByManagerIdAndRoleAndActiveTrue(actorId, Role.EMPLOYEE);
                rows = taskRepository.countByAssignerStatus(actorId);
            }
            case EMPLOYEE -> rows = taskRepository.countByAssigneeStatus(actorId);
            default -> throw new AccessDeniedException("Unsupported role.");
        }
        long pending = 0, inProgress = 0, done = 0;
        for (TaskStatusCount row : rows) {
            switch (row.getStatus()) {
                case PENDING -> pending = row.getTotal();
                case IN_PROGRESS -> inProgress = row.getTotal();
                case DONE -> done = row.getTotal();
            }
        }
        var counts = new DashboardResponse.StatusCounts(pending, inProgress, done);
        return new DashboardResponse(role, totalUsers, activeUsers, teamSize,
            pending + inProgress + done, done, counts);
    }
}
