package io.github.abdulmmh.etm.security;

import io.github.abdulmmh.etm.shared.error.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth2
            && "temporarily_unavailable".equals(oauth2.getError().getErrorCode())) {
            errorWriter.write(response, HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is temporarily unavailable.");
            return;
        }

        new BearerTokenAuthenticationEntryPoint().commence(request, response, exception);
        errorWriter.write(response, HttpStatus.UNAUTHORIZED,
            "Authentication is required or the token is invalid or expired.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        new BearerTokenAccessDeniedHandler().handle(request, response, exception);
        errorWriter.write(response, HttpStatus.FORBIDDEN, "You do not have permission for this action.");
    }
}
