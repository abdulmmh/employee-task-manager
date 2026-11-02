package io.github.abdulmmh.etm.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.abdulmmh.etm.task.*;
import io.github.abdulmmh.etm.user.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TaskReportServiceTest {
    @Mock UserRepository users;
    @Mock TaskRepository tasks;
    @Mock TaskPdfRenderer renderer;
    TaskReportService service;

    @BeforeEach void setup() { service = new TaskReportService(users, tasks, renderer); }

    @Test void adminExportsAllTasks() throws Exception {
        actor(Role.ADMIN);
        List<TaskReportRow> rows = List.of(row("Manager One"), row("Admin"));
        when(tasks.findAllReportRows()).thenReturn(rows);
        byte[] pdf = {1, 2, 3};
        when(renderer.render(eq("All tasks"), eq("Report User"), any(Instant.class), eq(rows))).thenReturn(pdf);
        assertThat(service.download(2)).isSameAs(pdf);
        verify(tasks, never()).findTeamReportRows(anyLong(), any());
    }

    @Test void managerExportsCurrentTeamRegardlessOfAssigner() throws Exception {
        actor(Role.MANAGER);
        List<TaskReportRow> rows = List.of(row("Manager One"), row("Admin"));
        when(tasks.findTeamReportRows(2, Role.EMPLOYEE)).thenReturn(rows);
        service.download(2);
        verify(renderer).render(eq("Own team - current employees, all assigners"),
            eq("Report User"), any(Instant.class), eq(rows));
        verify(tasks, never()).findAllReportRows();
    }

    @Test void emptyTeamStillGeneratesPdf() throws Exception {
        actor(Role.MANAGER);
        when(tasks.findTeamReportRows(2, Role.EMPLOYEE)).thenReturn(List.of());
        service.download(2);
        verify(renderer).render(anyString(), anyString(), any(Instant.class), eq(List.of()));
    }

    @Test void employeeCannotExportEvenThroughDirectServiceCall() {
        actor(Role.EMPLOYEE);
        assertThatThrownBy(() -> service.download(2)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks, renderer);
    }

    @Test void inactiveManagerCannotExport() {
        actor(Role.MANAGER).setActive(false);
        assertThatThrownBy(() -> service.download(2)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks, renderer);
    }

    @Test void deletedAccountCannotExport() {
        assertThatThrownBy(() -> service.download(2)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks, renderer);
    }

    private User actor(Role role) {
        var user = new User();
        user.setRole(role);
        user.setFullName("Report User");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        return user;
    }

    private TaskReportRow row(String assigner) {
        return new TaskReportRow(1L, "Task", "Employee One", assigner,
            TaskStatus.PENDING, TaskPriority.HIGH, null);
    }
}
