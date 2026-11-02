package io.github.abdulmmh.etm.auth;

import io.github.abdulmmh.etm.user.CreateUserRequest;
import io.github.abdulmmh.etm.user.UserResponse;
import io.github.abdulmmh.etm.user.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request) {
        var user = userService.createUser(Long.parseLong(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }
}
