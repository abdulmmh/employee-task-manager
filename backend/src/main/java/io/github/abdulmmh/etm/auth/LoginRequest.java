package io.github.abdulmmh.etm.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 150, message = "Email must be at most 150 characters.")
    String email,
    @NotBlank(message = "Password is required.")
    String password
) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}
