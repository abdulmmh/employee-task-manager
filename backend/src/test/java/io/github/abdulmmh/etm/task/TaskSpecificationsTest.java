package io.github.abdulmmh.etm.task;

import static org.mockito.Mockito.*;

import io.github.abdulmmh.etm.user.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

class TaskSpecificationsTest {
    @Test void employeeScopeRemainsWhenFilteringAnotherAssignee() {
        var root = root();
        var cb = mock(CriteriaBuilder.class);
        var scope = mock(Predicate.class);
        var filter = mock(Predicate.class);
        Path<Object> id = root.get("assignedTo").get("id");
        when(cb.equal(id, 3L)).thenReturn(scope);
        when(cb.equal(id, 6L)).thenReturn(filter);
        TaskSpecifications.visibleTo(3, Role.EMPLOYEE, null, null, 6L, null)
            .toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(id, 3L);
        verify(cb).equal(id, 6L);
        // Both predicates must be AND-ed; a requested assignee cannot replace ownership.
        verify(cb).and(null, scope);
        verify(cb).and(null, filter);
        verify(cb, never()).or(any(Predicate.class), any(Predicate.class));
    }

    @Test void managerScopeUsesOriginalAssignerAndFiltersNarrowIt() {
        var root = root();
        var cb = mock(CriteriaBuilder.class);
        TaskSpecifications.visibleTo(2, Role.MANAGER, TaskStatus.PENDING, TaskPriority.HIGH, 3L, null)
            .toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(root.get("assignedBy").get("id"), 2L);
        verify(cb).equal(root.get("assignedTo").get("id"), 3L);
        verify(cb).equal(root.get("status"), TaskStatus.PENDING);
        verify(cb).equal(root.get("priority"), TaskPriority.HIGH);
    }

    @Test void administratorHasNoOwnershipRestrictionButCanFilterRole() {
        var root = root();
        var cb = mock(CriteriaBuilder.class);
        TaskSpecifications.visibleTo(1, Role.ADMIN, null, null, null, Role.EMPLOYEE)
            .toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root, never()).get("assignedBy");
        verify(cb).equal(root.get("assignedTo").get("role"), Role.EMPLOYEE);
    }

    @SuppressWarnings("unchecked")
    private Root<Task> root() {
        return mock(Root.class, RETURNS_DEEP_STUBS);
    }
}
