package io.github.abdulmmh.etm.auth;

import io.github.abdulmmh.etm.user.Role;

public record LoginResponse(String token, Role role, String name) {
}
