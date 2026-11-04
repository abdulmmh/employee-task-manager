package io.github.abdulmmh.etm.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import io.github.abdulmmh.etm.task.repository.TaskRepository;
import io.github.abdulmmh.etm.user.dto.CreateUserRequest;
import io.github.abdulmmh.etm.user.dto.UpdateUserRequest;
import io.github.abdulmmh.etm.user.dto.UserResponse;
import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService service;
    private User admin;
    private User manager;
    private User employee;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, taskRepository, passwordEncoder);
        admin = user(1, Role.ADMIN);
        manager = user(2, Role.MANAGER);
        employee = user(3, Role.EMPLOYEE);
        employee.setManager(manager);
        employee.setPassword("existing-hash");
    }

    @Test
    void createsEmployeeWithEncodedPasswordAndNormalizedEmail() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));
        when(passwordEncoder.encode("Emp@123")).thenReturn("encoded-password");
        saveReturnsArgument();

        var response = service.createUser(1, new CreateUserRequest(
            " New Employee ", "NEW@DEMO.COM", "Emp@123", Role.EMPLOYEE, 2L));

        var saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getValue().getManager()).isSameAs(manager);
        assertThat(response.fullName()).isEqualTo("New Employee");
        assertThat(response.email()).isEqualTo("new@demo.com");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createsAdditionalAdministratorWithoutManager() {
        allowAdmin();
        when(passwordEncoder.encode("Admin2@123")).thenReturn("encoded-password");
        saveReturnsArgument();

        var response = service.createUser(1, new CreateUserRequest(
            "Second Admin", "admin2@demo.com", "Admin2@123", Role.ADMIN, null));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.managerId()).isNull();
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        allowAdmin();
        when(userRepository.existsByEmailIgnoreCase("admin@demo.com")).thenReturn(true);

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Duplicate", "ADMIN@DEMO.COM", "Admin@123", Role.ADMIN, null)), HttpStatus.CONFLICT);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void employeeRequiresManager() {
        allowAdmin();

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Employee", "new@demo.com", "Emp@123", Role.EMPLOYEE, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonEmployeeCannotHaveManager() {
        allowAdmin();

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Admin", "new@demo.com", "Admin@123", Role.ADMIN, 2L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInactiveManager() {
        allowAdmin();
        manager.setActive(false);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Employee", "new@demo.com", "Emp@123", Role.EMPLOYEE, 2L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAssigneeSelectionThatIsNotManager() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Employee", "new@demo.com", "Emp@123", Role.EMPLOYEE, 3L)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsPasswordOverBcryptByteLimitIncludingUnicode() {
        allowAdmin();

        assertStatus(() -> service.createUser(1, new CreateUserRequest(
            "Admin", "new@demo.com", "অ".repeat(25), Role.ADMIN, null)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void blankEditPasswordPreservesHashAndAllowsOrdinaryEdits() {
        allowEmployeeEdit();
        saveReturnsArgument();

        service.updateUser(1, 3, employeeEdit("   ", 2L));

        assertThat(employee.getPassword()).isEqualTo("existing-hash");
        assertThat(employee.getFullName()).isEqualTo("Updated Employee");
        verifyNoInteractions(passwordEncoder, taskRepository);
    }

    @Test
    void suppliedEditPasswordIsEncoded() {
        allowEmployeeEdit();
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("new-hash");
        saveReturnsArgument();

        service.updateUser(1, 3, employeeEdit("NewPassword@123", 2L));

        assertThat(employee.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void rejectsDuplicateEmailOnEdit() {
        allowEmployeeEdit();
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("user3@demo.com", 3L)).thenReturn(true);

        assertStatus(() -> service.updateUser(1, 3, employeeEdit(null, 2L)), HttpStatus.CONFLICT);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void cannotDeactivateLastActiveAdministrator() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        assertStatus(() -> service.deactivateUser(1, 1), HttpStatus.CONFLICT);
        assertThat(admin.isActive()).isTrue();
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void cannotDemoteLastActiveAdministrator() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        assertStatus(() -> service.updateUser(1, 1, new UpdateUserRequest(
            "Admin", "user1@demo.com", null, Role.MANAGER, null, true)), HttpStatus.CONFLICT);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anotherAdministratorAllowsDeactivation() {
        var otherAdmin = user(14, Role.ADMIN);
        when(userRepository.findActiveAdminsForUpdate(Role.ADMIN)).thenReturn(List.of(admin, otherAdmin));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        service.deactivateUser(14, 1);

        assertThat(admin.isActive()).isFalse();
        assertThat(otherAdmin.isActive()).isTrue();
        verify(userRepository).saveAndFlush(admin);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void rechecksLastAdministratorStateForEachMutation() {
        var otherAdmin = user(14, Role.ADMIN);
        when(userRepository.findActiveAdminsForUpdate(Role.ADMIN))
            .thenReturn(List.of(admin, otherAdmin), List.of(otherAdmin));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(otherAdmin));

        service.deactivateUser(1, 1);

        assertStatus(() -> service.deactivateUser(14, 14), HttpStatus.CONFLICT);
        assertThat(otherAdmin.isActive()).isTrue();
    }

    @Test
    void staleAdministratorCannotMutateAccounts() {
        when(userRepository.findActiveAdminsForUpdate(Role.ADMIN)).thenReturn(List.of(user(14, Role.ADMIN)));

        assertThatThrownBy(() -> service.deactivateUser(1, 3)).isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findByIdForUpdate(3L);
    }

    @Test
    void unfinishedTasksBlockRoleChange() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        when(taskRepository.existsUnfinishedForUser(3L, TaskStatus.DONE)).thenReturn(true);

        assertStatus(() -> service.updateUser(1, 3, new UpdateUserRequest(
            "Employee", "user3@demo.com", null, Role.MANAGER, null, true)), HttpStatus.CONFLICT);
        assertThat(employee.getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void completedHistoryDoesNotBlockRoleChange() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        when(taskRepository.existsUnfinishedForUser(3L, TaskStatus.DONE)).thenReturn(false);
        saveReturnsArgument();

        service.updateUser(1, 3, new UpdateUserRequest(
            "Employee", "user3@demo.com", null, Role.MANAGER, null, true));

        assertThat(employee.getRole()).isEqualTo(Role.MANAGER);
        assertThat(employee.getManager()).isNull();
    }

    @Test
    void unfinishedTasksBlockTeamTransfer() {
        allowAdmin();
        var newManager = user(5, Role.MANAGER);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(newManager));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        when(taskRepository.existsByAssignedToIdAndStatusNot(3L, TaskStatus.DONE)).thenReturn(true);

        assertStatus(() -> service.updateUser(1, 3, employeeEdit(null, 5L)), HttpStatus.CONFLICT);
        assertThat(employee.getManager()).isSameAs(manager);
    }

    @Test
    void allowsTransferAfterUnfinishedTasksAreResolved() {
        allowAdmin();
        var newManager = user(5, Role.MANAGER);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(newManager));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        saveReturnsArgument();

        var response = service.updateUser(1, 3, employeeEdit(null, 5L));

        assertThat(response.managerId()).isEqualTo(5L);
    }

    @Test
    void managerMustReassignActiveTeamBeforeRoleChange() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));
        when(userRepository.existsByManagerIdAndActiveTrue(2L)).thenReturn(true);

        assertStatus(() -> service.updateUser(1, 2, new UpdateUserRequest(
            "Manager", "user2@demo.com", null, Role.ADMIN, null, true)), HttpStatus.CONFLICT);
    }

    @Test
    void managerWithActiveTeamCannotBeDeactivated() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));
        when(userRepository.existsByManagerIdAndActiveTrue(2L)).thenReturn(true);

        assertStatus(() -> service.deactivateUser(1, 2), HttpStatus.CONFLICT);
        assertThat(manager.isActive()).isTrue();
    }

    @Test
    void employeeDeactivationPreservesRecordAndHistory() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));

        service.deactivateUser(1, 3);

        assertThat(employee.isActive()).isFalse();
        assertThat(employee.getId()).isEqualTo(3L);
        assertThat(employee.getManager()).isSameAs(manager);
        verify(userRepository, never()).delete(any(User.class));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void repeatedDeactivationIsIdempotent() {
        allowAdmin();
        employee.setActive(false);
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));

        service.deactivateUser(1, 3);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void returnsPaginationMetadataAndDtos() {
        var other = user(4, Role.EMPLOYEE);
        var page = new PageImpl<>(List.of(other, employee), PageRequest.of(1, 10), 12);
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), any(Pageable.class)))
            .thenReturn(page);

        var response = service.listUsers(1, 10, null, null, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).extracting(UserResponse::id).containsExactly(4L, 3L);
        var requestedPage = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), requestedPage.capture());
        assertThat(requestedPage.getValue().getPageNumber()).isEqualTo(1);
        assertThat(requestedPage.getValue().getPageSize()).isEqualTo(10);
        assertThat(requestedPage.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(requestedPage.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void rejectsInvalidPaginationBeforeQuery() {
        assertStatus(() -> service.listUsers(-1, 10, null, null, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listUsers(0, 101, null, null, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listUsers(0, 0, null, null, null), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    private void allowAdmin() {
        when(userRepository.findActiveAdminsForUpdate(Role.ADMIN)).thenReturn(List.of(admin));
    }

    private void allowEmployeeEdit() {
        allowAdmin();
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manager));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
    }

    private void saveReturnsArgument() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                ReflectionTestUtils.setField(user, "id", 13L);
            }
            return user;
        });
    }

    private UpdateUserRequest employeeEdit(String password, Long managerId) {
        return new UpdateUserRequest("Updated Employee", "user3@demo.com", password,
            Role.EMPLOYEE, managerId, true);
    }

    private User user(long id, Role role) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@demo.com");
        return user;
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
