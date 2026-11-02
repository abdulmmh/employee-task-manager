package io.github.abdulmmh.etm.user;

import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.shared.PageResponse;
import io.github.abdulmmh.etm.task.TaskRepository;
import io.github.abdulmmh.etm.task.TaskStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(int page, int size, String search, Role role, Boolean active) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }
        if (search != null && search.length() > 150) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Search must be at most 150 characters.");
        }
        var users = userRepository.findAll(UserSpecifications.matching(search, role, active),
            PageRequest.of(page, size, Sort.by("id").ascending()));
        return PageResponse.from(users.map(this::toResponse));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponse getUser(long id) {
        if (id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User id must be positive.");
        }
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        return toResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse createUser(long actorId, CreateUserRequest request) {
        lockAndVerifyAdministrator(actorId);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already in use.");
        }

        User manager = resolveManager(request.role(), request.managerId(), null);
        var user = new User();
        user.setFullName(request.fullName().strip());
        user.setEmail(email);
        user.setPassword(encodePassword(request.password()));
        user.setRole(request.role());
        user.setManager(manager);
        return toResponse(userRepository.saveAndFlush(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse updateUser(long actorId, long id, UpdateUserRequest request) {
        List<User> administrators = lockAndVerifyAdministrator(actorId);
        requirePositiveId(id);
        User manager = resolveManager(request.role(), request.managerId(), id);
        User user = findLockedUser(id);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already in use.");
        }

        boolean roleChanged = user.getRole() != request.role();
        boolean managerChanged = !Objects.equals(managerId(user.getManager()), managerId(manager));
        protectLastAdministrator(user, request.role(), request.active(), administrators.size());
        protectManagerTeam(user, roleChanged || !request.active());

        if (roleChanged && taskRepository.existsUnfinishedForUser(id, TaskStatus.DONE)) {
            throw new ApiException(HttpStatus.CONFLICT, "Complete or reassign unfinished tasks before changing this user's role.");
        }
        if (!roleChanged && user.getRole() == Role.EMPLOYEE && managerChanged
            && taskRepository.existsByAssignedToIdAndStatusNot(id, TaskStatus.DONE)) {
            throw new ApiException(HttpStatus.CONFLICT, "Complete or reassign unfinished tasks before transferring this employee.");
        }

        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(encodePassword(request.password()));
        }
        user.setFullName(request.fullName().strip());
        user.setEmail(email);
        user.setRole(request.role());
        user.setManager(manager);
        user.setActive(request.active());
        return toResponse(userRepository.saveAndFlush(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deactivateUser(long actorId, long id) {
        List<User> administrators = lockAndVerifyAdministrator(actorId);
        requirePositiveId(id);
        User user = findLockedUser(id);
        if (!user.isActive()) {
            return;
        }
        protectLastAdministrator(user, user.getRole(), false, administrators.size());
        protectManagerTeam(user, true);
        user.setActive(false);
        userRepository.saveAndFlush(user);
    }

    private List<User> lockAndVerifyAdministrator(long actorId) {
        // Serialize administrative writes so concurrent requests cannot remove all admins.
        List<User> administrators = userRepository.findActiveAdminsForUpdate(Role.ADMIN);
        if (administrators.stream().noneMatch(user -> user.getId().equals(actorId))) {
            throw new AccessDeniedException("An active administrator is required.");
        }
        return administrators;
    }

    private void protectLastAdministrator(User user, Role newRole, boolean newActive, int administratorCount) {
        if (user.isActive() && user.getRole() == Role.ADMIN
            && (!newActive || newRole != Role.ADMIN) && administratorCount <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "The last active administrator cannot be deactivated or demoted.");
        }
    }

    private void protectManagerTeam(User user, boolean leavingManagerRole) {
        if (user.getRole() == Role.MANAGER && leavingManagerRole
            && userRepository.existsByManagerIdAndActiveTrue(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Reassign the manager's active team before changing their role or deactivating them.");
        }
    }

    private User resolveManager(Role role, Long managerId, Long targetId) {
        if (role != Role.EMPLOYEE) {
            if (managerId != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only employees can have a manager.");
            }
            return null;
        }
        if (managerId == null || managerId <= 0 || managerId.equals(targetId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Employees require a valid manager other than themselves.");
        }
        User manager = userRepository.findByIdForUpdate(managerId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Select an active manager."));
        if (manager.getRole() != Role.MANAGER || !manager.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active manager.");
        }
        return manager;
    }

    private User findLockedUser(long id) {
        return userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private String encodePassword(String password) {
        if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is required and must not exceed 72 UTF-8 bytes.");
        }
        return passwordEncoder.encode(password);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User id must be positive.");
        }
    }

    private Long managerId(User manager) {
        return manager == null ? null : manager.getId();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
            managerId(user.getManager()), user.isActive(), user.getCreatedAt());
    }
}
