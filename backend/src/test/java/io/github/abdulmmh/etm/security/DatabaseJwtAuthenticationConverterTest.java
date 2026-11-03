package io.github.abdulmmh.etm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class DatabaseJwtAuthenticationConverterTest {

    @Mock
    private UserRepository userRepository;

    private DatabaseJwtAuthenticationConverter converter;
    private User user;

    @BeforeEach
    void setUp() {
        converter = new DatabaseJwtAuthenticationConverter(userRepository);
        user = new User();
        user.setEmail("emp1@demo.com");
        user.setRole(Role.EMPLOYEE);
    }

    @Test
    void usesDatabaseRoleInsteadOfTokenRole() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        var authentication = converter.convert(token("3", "ADMIN"));

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_EMPLOYEE");
    }

    @Test
    void reloadsRoleOnEachRequest() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        Jwt token = token("3", "EMPLOYEE");
        assertThat(converter.convert(token).getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_EMPLOYEE");

        user.setRole(Role.MANAGER);

        assertThat(converter.convert(token).getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_MANAGER");
    }

    @Test
    void rejectsInactiveAccount() {
        user.setActive(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(token("3", "EMPLOYEE")))
            .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rejectsMissingAccount() {
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(token("3", "EMPLOYEE")))
            .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rejectsInvalidSubjectBeforeDatabaseLookup() {
        assertThatThrownBy(() -> converter.convert(token("not-a-user-id", "ADMIN")))
            .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(token("-1", "ADMIN")))
            .isInstanceOf(InvalidBearerTokenException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void distinguishesDatabaseFailureFromBadCredentials() {
        when(userRepository.findById(3L)).thenThrow(new DataAccessResourceFailureException("Unavailable"));

        assertThatThrownBy(() -> converter.convert(token("3", "EMPLOYEE")))
            .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                assertThat(exception.getError().getErrorCode()).isEqualTo("temporarily_unavailable"));
    }

    private Jwt token(String subject, String role) {
        return Jwt.withTokenValue("converter-test-token").header("alg", "HS256")
            .subject(subject).claim("role", role).build();
    }
}
