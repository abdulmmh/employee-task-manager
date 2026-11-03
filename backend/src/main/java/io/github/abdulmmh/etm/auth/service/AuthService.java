package io.github.abdulmmh.etm.auth.service;

import io.github.abdulmmh.etm.auth.dto.LoginRequest;
import io.github.abdulmmh.etm.auth.dto.LoginResponse;
import io.github.abdulmmh.etm.security.JwtService;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.email().strip(), request.password()));

        var user = userRepository.findByEmailIgnoreCase(authentication.getName())
            .filter(found -> found.isActive())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials."));

        return new LoginResponse(jwtService.issue(user), user.getRole(), user.getFullName());
    }
}
