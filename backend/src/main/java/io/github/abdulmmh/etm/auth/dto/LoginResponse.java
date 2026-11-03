package io.github.abdulmmh.etm.auth.dto;

import io.github.abdulmmh.etm.user.enums.Role;

public record LoginResponse(String token, Role role, String name) {
}
