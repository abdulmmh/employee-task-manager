package io.github.abdulmmh.etm.team.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.team.dto.EmployeeOption;
import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {
    @Mock UserRepository users;
    TeamService service;
    @BeforeEach void setup() { service = new TeamService(users); }

    @Test void managerReceivesMinimalPaginatedEmployeeOptions() {
        actor(Role.MANAGER);
        var employee = new User();
        ReflectionTestUtils.setField(employee, "id", 3L);
        employee.setFullName("Employee One");
        when(users.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(employee), PageRequest.of(0, 20), 1));
        var result = service.employees(2, 0, 20, null);
        assertThat(result.content()).containsExactly(new EmployeeOption(3L, "Employee One"));
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test void employeeCannotUseLookupEvenThroughDirectService() {
        actor(Role.EMPLOYEE);
        assertThatThrownBy(() -> service.employees(2, 0, 20, null)).isInstanceOf(AccessDeniedException.class);
        verify(users, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test void inactiveManagerCannotUseLookup() {
        actor(Role.MANAGER).setActive(false);
        assertThatThrownBy(() -> service.employees(2, 0, 20, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void missingUserCannotUseLookup() {
        assertThatThrownBy(() -> service.employees(2, 0, 20, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void rejectsInvalidPaginationAndOversizedSearch() {
        assertThatThrownBy(() -> service.employees(2, -1, 20, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.employees(2, 0, 101, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.employees(2, 0, 20, "a".repeat(101))).isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }

    @Test void managerQueryAlwaysIncludesTeamRoleAndActiveRestrictions() {
        Root<User> root = root();
        var cb = mock(CriteriaBuilder.class);
        TeamService.eligibleEmployees(2, Role.MANAGER, null).toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).equal(root.get("manager").get("id"), 2L);
        verify(cb).equal(root.get("role"), Role.EMPLOYEE);
        verify(cb).isTrue(root.get("active"));
    }

    @Test void adminQueryOmitsTeamRestrictionButKeepsActiveEmployeeRestrictions() {
        Root<User> root = root();
        var cb = mock(CriteriaBuilder.class);
        TeamService.eligibleEmployees(1, Role.ADMIN, null).toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root, never()).get("manager");
        verify(cb).equal(root.get("role"), Role.EMPLOYEE);
        verify(cb).isTrue(root.get("active"));
    }

    private User actor(Role role) {
        var user = new User(); user.setRole(role);
        when(users.findById(2L)).thenReturn(Optional.of(user));
        return user;
    }
    @SuppressWarnings("unchecked") private Root<User> root() { return mock(Root.class, RETURNS_DEEP_STUBS); }
}
