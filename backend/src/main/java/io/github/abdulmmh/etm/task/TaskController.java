package io.github.abdulmmh.etm.task;

import io.github.abdulmmh.etm.shared.PageResponse;
import io.github.abdulmmh.etm.user.Role;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assignee,
            @RequestParam(required = false) Role role) {
        return taskService.list(actor(jwt), page, size, status, priority, assignee, role);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return taskService.get(actor(jwt), id);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskRequest request) {
        var task = taskService.create(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody TaskRequest request) {
        return taskService.update(actor(jwt), id, request);
    }

    @PutMapping("/{id}/status")
    public TaskResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody TaskStatusRequest request) {
        return taskService.changeStatus(actor(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        taskService.delete(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private long actor(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
