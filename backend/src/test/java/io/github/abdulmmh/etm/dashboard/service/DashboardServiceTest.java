package io.github.abdulmmh.etm.dashboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.abdulmmh.etm.dashboard.dto.DashboardResponse;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import io.github.abdulmmh.etm.task.repository.TaskRepository;
import io.github.abdulmmh.etm.task.repository.TaskStatusCount;
import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock UserRepository users;
    @Mock TaskRepository tasks;
    DashboardService service;

    @BeforeEach void setup() { service = new DashboardService(users, tasks); }

    @Test void adminCountsIncludeInactiveUsersAndAllTasks() {
        actor(Role.ADMIN);
        when(users.count()).thenReturn(13L);
        when(users.countByActiveTrue()).thenReturn(12L);
        when(tasks.countAllByStatus()).thenReturn(List.of(row(TaskStatus.PENDING, 9),
            row(TaskStatus.IN_PROGRESS, 7), row(TaskStatus.DONE, 8)));
        var result = service.stats(1);
        assertThat(result.totalUsers()).isEqualTo(13L);
        assertThat(result.activeUsers()).isEqualTo(12L);
        assertThat(result.totalTasks()).isEqualTo(24);
        assertThat(result.completedTasks()).isEqualTo(8);
        assertThat(result.tasksByStatus().pending()).isEqualTo(9);
        assertThat(result.teamSize()).isNull();
        verify(tasks, never()).countByAssigneeStatus(anyLong());
        verify(tasks, never()).countByAssignerStatus(anyLong());
    }

    @Test void managerGetsOnlyAssignedCountsAndActiveTeamSize() {
        actor(Role.MANAGER);
        when(users.countByManagerIdAndRoleAndActiveTrue(1, Role.EMPLOYEE)).thenReturn(3L);
        when(tasks.countByAssignerStatus(1)).thenReturn(List.of(row(TaskStatus.PENDING, 4), row(TaskStatus.DONE, 2)));
        var result = service.stats(1);
        assertThat(result.teamSize()).isEqualTo(3L);
        assertThat(result.totalTasks()).isEqualTo(6);
        assertThat(result.tasksByStatus().inProgress()).isZero();
        assertThat(result.totalUsers()).isNull();
        assertThat(result.activeUsers()).isNull();
        verify(tasks, never()).countAllByStatus();
        verify(tasks, never()).countByAssigneeStatus(anyLong());
        verify(users, never()).count();
    }

    @Test void employeeGetsOnlyOwnCountsWithoutTeamOrGlobalTotals() {
        actor(Role.EMPLOYEE);
        when(tasks.countByAssigneeStatus(1)).thenReturn(List.of(row(TaskStatus.IN_PROGRESS, 2)));
        var result = service.stats(1);
        assertThat(result.totalTasks()).isEqualTo(2);
        assertThat(result.completedTasks()).isZero();
        assertThat(result.tasksByStatus().inProgress()).isEqualTo(2);
        assertThat(result.teamSize()).isNull();
        assertThat(result.totalUsers()).isNull();
        verify(tasks, never()).countAllByStatus();
        verify(tasks, never()).countByAssignerStatus(anyLong());
        verify(users, never()).countByManagerIdAndRoleAndActiveTrue(anyLong(), any());
    }

    @Test void emptyResultsReturnAllThreeStatusesAsZero() {
        actor(Role.EMPLOYEE);
        when(tasks.countByAssigneeStatus(1)).thenReturn(List.of());
        var result = service.stats(1);
        assertThat(result.totalTasks()).isZero();
        assertThat(result.tasksByStatus()).isEqualTo(new DashboardResponse.StatusCounts(0, 0, 0));
    }

    @Test void inactiveAccountCannotReadCounts() {
        var user = actor(Role.ADMIN);
        user.setActive(false);
        assertThatThrownBy(() -> service.stats(1)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
        verify(users, never()).count();
    }

    @Test void missingAccountCannotReadCounts() {
        assertThatThrownBy(() -> service.stats(1)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    private User actor(Role role) {
        var user = new User();
        user.setRole(role);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    private TaskStatusCount row(TaskStatus status, long count) {
        return new TaskStatusCount() {
            public TaskStatus getStatus() { return status; }
            public long getTotal() { return count; }
        };
    }
}
