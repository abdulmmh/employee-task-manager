package io.github.abdulmmh.etm.user.dto;

import io.github.abdulmmh.etm.user.enums.Role;
import java.time.LocalDateTime;

public record UserResponse(Long id, String fullName, String email, Role role,
                           Long managerId, boolean active, LocalDateTime createdAt) {
}
