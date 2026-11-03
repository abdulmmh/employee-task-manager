package io.github.abdulmmh.etm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdulmmh.etm.user.entity.User;
import io.github.abdulmmh.etm.user.enums.Role;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtConfiguration configuration;
    private JwtProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        configuration = new JwtConfiguration();
        String secret = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        properties = new JwtProperties(secret, "employee-task-manager", 1800);
        var key = configuration.jwtSigningKey(properties);
        encoder = configuration.jwtEncoder(key);
        decoder = configuration.jwtDecoder(key, properties);
    }

    @Test
    void issuesSignedTokenWithStableUserIdAndConfiguredLifetime() {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", 3L);
        user.setRole(Role.EMPLOYEE);

        var token = decoder.decode(new JwtService(encoder, properties).issue(user));

        assertThat(token.getSubject()).isEqualTo("3");
        assertThat(token.getClaimAsString("role")).isEqualTo("EMPLOYEE");
        assertThat(token.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt()).getSeconds()).isEqualTo(1800);
        assertThat(token.getClaims()).doesNotContainKeys("password", "email");
    }

    @Test
    void rejectsSignatureFromAnotherKey() {
        String differentSecret = Base64.getEncoder().encodeToString(
            "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
        var differentKey = configuration.jwtSigningKey(
            new JwtProperties(differentSecret, properties.issuer(), 1800));
        var otherEncoder = configuration.jwtEncoder(differentKey);

        String token = sign(otherEncoder, validClaims().build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = sign(encoder, validClaims()
            .issuedAt(Instant.now().minusSeconds(120))
            .expiresAt(Instant.now().minusSeconds(60)).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenNotValidYet() {
        String token = sign(encoder, validClaims().notBefore(Instant.now().plusSeconds(600)).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnotherIssuer() {
        String token = sign(encoder, validClaims().issuer("another-service").build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = sign(encoder, JwtClaimsSet.builder()
            .issuer(properties.issuer()).subject("3").issuedAt(Instant.now()).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesMalformedOrWeakSigningSecret() {
        assertThatThrownBy(() -> configuration.jwtSigningKey(
            new JwtProperties("not-base64!", properties.issuer(), 1800)))
            .isInstanceOf(IllegalStateException.class);

        String weakSecret = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> configuration.jwtSigningKey(
            new JwtProperties(weakSecret, properties.issuer(), 1800)))
            .isInstanceOf(IllegalStateException.class);
    }

    private JwtClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer(properties.issuer()).subject("3")
            .issuedAt(now).expiresAt(now.plusSeconds(1800));
    }

    private String sign(JwtEncoder tokenEncoder, JwtClaimsSet claims) {
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return tokenEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
