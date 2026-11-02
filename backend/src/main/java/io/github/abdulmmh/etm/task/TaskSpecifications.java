package io.github.abdulmmh.etm.task;

import io.github.abdulmmh.etm.user.Role;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {
    private TaskSpecifications() {}

    public static Specification<Task> visibleTo(long actorId, Role actorRole,
            TaskStatus status, TaskPriority priority, Long assignee, Role role) {
        return (root, query, cb) -> {
            var result = cb.conjunction();
            // Client filters always narrow the server's ownership scope.
            if (actorRole == Role.MANAGER) {
                result = cb.and(result, cb.equal(root.get("assignedBy").get("id"), actorId));
            } else if (actorRole == Role.EMPLOYEE) {
                result = cb.and(result, cb.equal(root.get("assignedTo").get("id"), actorId));
            }
            if (status != null) result = cb.and(result, cb.equal(root.get("status"), status));
            if (priority != null) result = cb.and(result, cb.equal(root.get("priority"), priority));
            if (assignee != null) result = cb.and(result, cb.equal(root.get("assignedTo").get("id"), assignee));
            if (role != null) result = cb.and(result, cb.equal(root.get("assignedTo").get("role"), role));
            return result;
        };
    }
}
