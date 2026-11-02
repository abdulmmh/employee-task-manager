package io.github.abdulmmh.etm.user;

import io.github.abdulmmh.etm.shared.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponse getUser(long id) {
        if (id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User id must be positive.");
        }
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        Long managerId = user.getManager() == null ? null : user.getManager().getId();
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
            user.getRole(), managerId, user.isActive(), user.getCreatedAt());
    }
}
