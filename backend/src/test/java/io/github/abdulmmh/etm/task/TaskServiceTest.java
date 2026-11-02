package io.github.abdulmmh.etm.task;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.user.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository tasks;
    @Mock UserRepository users;
    TaskService service;
    User admin, manager, employee, otherManager, otherEmployee;
    Task task;

    @BeforeEach
    void setup() {
        service = new TaskService(tasks, users);
        admin = user(1, Role.ADMIN);
        manager = user(2, Role.MANAGER);
        employee = user(3, Role.EMPLOYEE);
        otherManager = user(5, Role.MANAGER);
        otherEmployee = user(6, Role.EMPLOYEE);
        employee.setManager(manager);
        otherEmployee.setManager(otherManager);
        task = new Task();
        ReflectionTestUtils.setField(task, "id", 20L);
        task.setTitle("Original");
        task.setAssignedBy(manager);
        task.setAssignedTo(employee);
    }

    @Test void managerCreatesPendingTaskInOwnTeam() {
        actor(manager);
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        save();
        var result = service.create(2, request(3));
        assertThat(result.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.assignedBy()).isEqualTo(2L);
        assertThat(result.title()).isEqualTo("New task");
    }

    @Test void adminCanAssignAcrossTeams() {
        actor(admin);
        when(users.findByIdForUpdate(6L)).thenReturn(Optional.of(otherEmployee));
        save();
        assertThat(service.create(1, request(6)).assignedTo()).isEqualTo(6L);
    }

    @Test void managerCannotAssignToAnotherTeam() {
        actor(manager);
        when(users.findByIdForUpdate(6L)).thenReturn(Optional.of(otherEmployee));
        assertThatThrownBy(() -> service.create(2, request(6))).isInstanceOf(AccessDeniedException.class);
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test void employeeCannotCreateEvenWhenServiceCalledDirectly() {
        actor(employee);
        assertThatThrownBy(() -> service.create(3, request(3))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void inactiveAssigneeRejected() {
        actor(manager);
        employee.setActive(false);
        when(users.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        conflict(() -> service.create(2, request(3)), HttpStatus.BAD_REQUEST);
    }

    @Test void nonEmployeeAssigneeRejected() {
        actor(admin);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));
        conflict(() -> service.create(1, request(2)), HttpStatus.BAD_REQUEST);
    }

    @Test void unknownAssigneeRejected() {
        actor(admin);
        conflict(() -> service.create(1, request(99)), HttpStatus.BAD_REQUEST);
    }

    @Test void managerCanViewOwnAssignedTask() {
        readable(manager);
        assertThat(service.get(2, 20).id()).isEqualTo(20L);
    }

    @Test void managerCannotViewAnotherManagersTask() {
        readable(otherManager);
        assertThatThrownBy(() -> service.get(5, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void employeeCanViewOwnTask() {
        readable(employee);
        assertThat(service.get(3, 20).assignedTo()).isEqualTo(3L);
    }

    @Test void employeeCannotViewOtherEmployeesTask() {
        readable(otherEmployee);
        assertThatThrownBy(() -> service.get(6, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void adminCanViewAnyTask() {
        readable(admin);
        assertThat(service.get(1, 20).id()).isEqualTo(20L);
    }

    @Test void missingTaskReturns404() {
        actor(admin);
        conflict(() -> service.get(1, 99), HttpStatus.NOT_FOUND);
    }

    @Test void inactiveActorCannotRead() {
        actor(employee);
        employee.setActive(false);
        assertThatThrownBy(() -> service.get(3, 20)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    @Test void ownerManagerCanEditWithoutChangingAssignerOrStatus() {
        writable(manager);
        task.setStatus(TaskStatus.IN_PROGRESS);
        save();
        var result = service.update(2, 20, request(3));
        assertThat(result.title()).isEqualTo("New task");
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.assignedBy()).isEqualTo(2L);
    }

    @Test void anotherManagerCannotEdit() {
        writable(otherManager);
        assertThatThrownBy(() -> service.update(5, 20, request(3))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void pendingTaskCanBeReassignedWithinOriginalTeam() {
        writable(admin);
        User colleague = user(4, Role.EMPLOYEE);
        colleague.setManager(manager);
        when(users.findByIdForUpdate(4L)).thenReturn(Optional.of(colleague));
        save();
        var result = service.update(1, 20, request(4));
        assertThat(result.assignedTo()).isEqualTo(4L);
        assertThat(result.assignedBy()).isEqualTo(2L);
    }

    @Test void evenAdminCannotMoveManagersTaskToAnotherTeam() {
        writable(admin);
        when(users.findByIdForUpdate(6L)).thenReturn(Optional.of(otherEmployee));
        conflict(() -> service.update(1, 20, request(6)), HttpStatus.CONFLICT);
    }

    @Test void inProgressTaskCannotBeReassigned() {
        writable(admin);
        task.setStatus(TaskStatus.IN_PROGRESS);
        conflict(() -> service.update(1, 20, request(6)), HttpStatus.CONFLICT);
        verify(users, never()).findByIdForUpdate(6L);
    }

    @Test void doneTaskCannotBeReassigned() {
        writable(admin);
        task.setStatus(TaskStatus.DONE);
        conflict(() -> service.update(1, 20, request(6)), HttpStatus.CONFLICT);
    }

    @Test void employeeStartsOwnTask() {
        writable(employee);
        save();
        assertThat(service.changeStatus(3, 20, new TaskStatusRequest(TaskStatus.IN_PROGRESS)).status())
            .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test void employeeCompletesOwnStartedTask() {
        writable(employee);
        task.setStatus(TaskStatus.IN_PROGRESS);
        save();
        assertThat(service.changeStatus(3, 20, new TaskStatusRequest(TaskStatus.DONE)).status())
            .isEqualTo(TaskStatus.DONE);
    }

    @Test void employeeCannotSkipStatus() {
        writable(employee);
        conflict(() -> service.changeStatus(3, 20, new TaskStatusRequest(TaskStatus.DONE)), HttpStatus.CONFLICT);
    }

    @Test void employeeCannotReverseStatus() {
        writable(employee);
        task.setStatus(TaskStatus.DONE);
        conflict(() -> service.changeStatus(3, 20, new TaskStatusRequest(TaskStatus.IN_PROGRESS)), HttpStatus.CONFLICT);
    }

    @Test void sameStatusRetryIsIdempotent() {
        writable(employee);
        assertThat(service.changeStatus(3, 20, new TaskStatusRequest(TaskStatus.PENDING)).status())
            .isEqualTo(TaskStatus.PENDING);
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test void employeeCannotChangeOthersStatus() {
        writable(otherEmployee);
        assertThatThrownBy(() -> service.changeStatus(6, 20, new TaskStatusRequest(TaskStatus.IN_PROGRESS)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test void adminCannotChangeStatus() {
        actor(admin);
        assertThatThrownBy(() -> service.changeStatus(1, 20, new TaskStatusRequest(TaskStatus.IN_PROGRESS)))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    @Test void onlyAdminDeletesTask() {
        writable(admin);
        service.delete(1, 20);
        verify(tasks).delete(task);
        verify(tasks).flush();
    }

    @Test void managerCannotDeleteEvenOwnTask() {
        actor(manager);
        assertThatThrownBy(() -> service.delete(2, 20)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    @Test void listReturnsPageMetadataAndDtos() {
        actor(employee);
        when(tasks.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 5), 1));
        var result = service.list(3, 0, 5, TaskStatus.PENDING, TaskPriority.MEDIUM, 3L, null);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).assignedTo()).isEqualTo(3L);
    }

    @Test void invalidPaginationRejectedBeforeQuery() {
        conflict(() -> service.list(1, -1, 5, null, null, null, null), HttpStatus.BAD_REQUEST);
        conflict(() -> service.list(1, 0, 101, null, null, null, null), HttpStatus.BAD_REQUEST);
        conflict(() -> service.list(1, 0, 5, null, null, 0L, null), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tasks, users);
    }

    private void actor(User user) {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }
    private void readable(User user) {
        actor(user);
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
    }
    private void writable(User user) {
        actor(user);
        when(tasks.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
    }
    private void save() {
        when(tasks.saveAndFlush(any(Task.class))).thenAnswer(call -> call.getArgument(0));
    }
    private TaskRequest request(long assignee) {
        return new TaskRequest(" New task ", "Description", TaskPriority.HIGH, null, assignee);
    }
    private User user(long id, Role role) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id);
        user.setRole(role);
        return user;
    }
    private void conflict(Runnable operation, HttpStatus status) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class,
            error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
