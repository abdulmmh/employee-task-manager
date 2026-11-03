package io.github.abdulmmh.etm.task.service;

import io.github.abdulmmh.etm.shared.dto.PageResponse;
import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.task.dto.TaskRequest;
import io.github.abdulmmh.etm.task.dto.TaskResponse;
import io.github.abdulmmh.etm.task.dto.TaskStatusRequest;
import io.github.abdulmmh.etm.task.entity.Task;
import io.github.abdulmmh.etm.task.enums.TaskPriority;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import io.github.abdulmmh.etm.task.repository.TaskRepository;
import io.github.abdulmmh.etm.task.repository.TaskSpecifications;
import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(long actorId, int page, int size, TaskStatus status,
            TaskPriority priority, Long assignee, Role role) {
        if (page < 0 || size < 1 || size > 100 || (assignee != null && assignee <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a non-negative page, size 1–100 and a positive assignee id.");
        }
        User actor = actor(actorId);
        var tasks = taskRepository.findAll(
            TaskSpecifications.visibleTo(actorId, actor.getRole(), status, priority, assignee, role),
            PageRequest.of(page, size, Sort.by("id").descending()));
        return PageResponse.from(tasks.map(this::response));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EMPLOYEE')")
    @Transactional(readOnly = true)
    public TaskResponse get(long actorId, long id) {
        User actor = actor(actorId);
        Task task = task(id, false);
        requireVisibility(actor, task);
        return response(task);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    public TaskResponse create(long actorId, TaskRequest request) {
        User actor = writeActor(actorId);
        requireAssigner(actor);
        User employee = assignee(actor, request.assignedTo());
        var task = new Task();
        task.setAssignedBy(actor);
        task.setAssignedTo(employee);
        apply(task, request);
        return response(taskRepository.saveAndFlush(task));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    public TaskResponse update(long actorId, long id, TaskRequest request) {
        User actor = writeActor(actorId);
        requireAssigner(actor);
        Task task = task(id, true);
        requireVisibility(actor, task);
        boolean reassigning = !Objects.equals(task.getAssignedTo().getId(), request.assignedTo());
        if (reassigning) {
            if (task.getStatus() != TaskStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "Only pending tasks can be reassigned.");
            }
            User employee = assignee(actor, request.assignedTo());
            // A manager must retain control of their own unfinished task after reassignment.
            if (task.getAssignedBy().getRole() == Role.MANAGER
                && (employee.getManager() == null
                    || !employee.getManager().getId().equals(task.getAssignedBy().getId()))) {
                throw new ApiException(HttpStatus.CONFLICT, "Reassign within the original manager's team to preserve task ownership.");
            }
            task.setAssignedTo(employee);
        }
        apply(task, request);
        return response(taskRepository.saveAndFlush(task));
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public TaskResponse changeStatus(long actorId, long id, TaskStatusRequest request) {
        User actor = writeActor(actorId);
        if (actor.getRole() != Role.EMPLOYEE) throw denied();
        Task task = task(id, true);
        if (!task.getAssignedTo().getId().equals(actorId)) throw denied();
        TaskStatus current = task.getStatus();
        TaskStatus next = request.status();
        if (current == next) return response(task);
        if (!((current == TaskStatus.PENDING && next == TaskStatus.IN_PROGRESS)
            || (current == TaskStatus.IN_PROGRESS && next == TaskStatus.DONE))) {
            throw new ApiException(HttpStatus.CONFLICT, "Follow PENDING → IN_PROGRESS → DONE; skipping or reversing is not allowed.");
        }
        task.setStatus(next);
        return response(taskRepository.saveAndFlush(task));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(long actorId, long id) {
        User actor = writeActor(actorId);
        if (actor.getRole() != Role.ADMIN) throw denied();
        taskRepository.delete(task(id, true));
        taskRepository.flush();
    }

    private User writeActor(long id) {
        // Share UserService's first lock to serialize writes with role/team changes.
        // This small demo favors predictable consistency over write throughput.
        userRepository.findActiveAdminsForUpdate(Role.ADMIN);
        return actor(id);
    }

    private User actor(long id) {
        return userRepository.findById(id).filter(User::isActive).orElseThrow(this::denied);
    }

    private void requireAssigner(User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) throw denied();
    }

    private User assignee(User actor, Long id) {
        if (id == null || id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active employee.");
        }
        User employee = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Select an active employee."));
        if (!employee.isActive() || employee.getRole() != Role.EMPLOYEE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active employee.");
        }
        if (actor.getRole() == Role.MANAGER && (employee.getManager() == null
            || !employee.getManager().getId().equals(actor.getId()))) throw denied();
        return employee;
    }

    private Task task(long id, boolean lock) {
        if (id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Task id must be positive.");
        return (lock ? taskRepository.findByIdForUpdate(id) : taskRepository.findById(id))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found."));
    }

    private void requireVisibility(User actor, Task task) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && task.getAssignedBy().getId().equals(actor.getId())) return;
        if (actor.getRole() == Role.EMPLOYEE && task.getAssignedTo().getId().equals(actor.getId())) return;
        throw denied();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("You do not have permission to access this task.");
    }

    private void apply(Task task, TaskRequest request) {
        task.setTitle(request.title().strip());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDeadline(request.deadline());
    }

    private TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
            task.getPriority(), task.getDeadline(), task.getAssignedTo().getId(),
            task.getAssignedTo().getFullName(), task.getAssignedBy().getId(),
            task.getAssignedBy().getFullName(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
