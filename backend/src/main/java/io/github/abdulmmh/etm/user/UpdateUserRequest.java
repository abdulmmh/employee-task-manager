package io.github.abdulmmh.etm.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @NotBlank(message = "Full name is required.")
    @Size(max = 100, message = "Full name must be at most 100 characters.")
    String fullName,
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 150, message = "Email must be at most 150 characters.")
    String email,
    String password,
    @NotNull(message = "Role is required.") Role role,
    @Positive(message = "Manager id must be positive.") Long managerId,
    @NotNull(message = "Active state is required.") Boolean active
) {

    @Override
    public String toString() {
        return "UpdateUserRequest[email=" + email + ", role=" + role + ", password=[REDACTED]]";
    }
}
