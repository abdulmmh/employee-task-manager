package io.github.abdulmmh.etm.security;

import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("The access token is invalid.");
        }
        if (userId <= 0) {
            throw new InvalidBearerTokenException("The access token is invalid.");
        }

        try {
            var user = userRepository.findById(userId)
                .filter(found -> found.isActive())
                .orElseThrow(() -> new InvalidBearerTokenException("The access token is invalid."));

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            return new JwtAuthenticationToken(jwt, authorities, user.getEmail());
        } catch (DataAccessException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"),
                "Authentication service is temporarily unavailable.");
        }
    }
}
