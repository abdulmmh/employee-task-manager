package io.github.abdulmmh.etm.user.controller;

import io.github.abdulmmh.etm.shared.dto.PageResponse;
import io.github.abdulmmh.etm.user.dto.CreateUserRequest;
import io.github.abdulmmh.etm.user.dto.UpdateUserRequest;
import io.github.abdulmmh.etm.user.dto.UserResponse;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {
        return userService.listUsers(page, size, search, role, active);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable long id) {
        return userService.getUser(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request) {
        var user = userService.createUser(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(Long.parseLong(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUser(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        userService.deactivateUser(Long.parseLong(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}
