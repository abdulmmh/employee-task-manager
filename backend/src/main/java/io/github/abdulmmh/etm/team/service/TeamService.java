package io.github.abdulmmh.etm.team.service;

import io.github.abdulmmh.etm.shared.dto.PageResponse;
import io.github.abdulmmh.etm.shared.error.ApiException;
import io.github.abdulmmh.etm.team.dto.EmployeeOption;
import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final UserRepository users;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional(readOnly = true)
    public PageResponse<EmployeeOption> employees(long actorId, int page, int size, String search) {
        if (page < 0 || size < 1 || size > 100 || (search != null && search.length() > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a non-negative page, size 1–100 and a search of at most 100 characters.");
        }
        var actor = users.findById(actorId).filter(User::isActive)
            .orElseThrow(() -> new AccessDeniedException("An active manager or administrator is required."));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("An active manager or administrator is required.");
        }
        var employees = users.findAll(eligibleEmployees(actorId, actor.getRole(), search),
            PageRequest.of(page, size, Sort.by("fullName").ascending().and(Sort.by("id"))));
        return PageResponse.from(employees.map(user -> new EmployeeOption(user.getId(), user.getFullName())));
    }

    static Specification<User> eligibleEmployees(long actorId, Role role, String search) {
        return (root, query, cb) -> {
            var scope = cb.and(cb.equal(root.get("role"), Role.EMPLOYEE), cb.isTrue(root.get("active")));
            if (role == Role.MANAGER) scope = cb.and(scope, cb.equal(root.get("manager").get("id"), actorId));
            if (search != null && !search.isBlank()) {
                String term = search.strip().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                scope = cb.and(scope, cb.like(cb.lower(root.get("fullName")), "%" + term + "%", '\\'));
            }
            return scope;
        };
    }
}
