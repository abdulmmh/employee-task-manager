package io.github.abdulmmh.etm.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdulmmh.etm.security.DatabaseJwtAuthenticationConverter;
import io.github.abdulmmh.etm.security.JwtConfiguration;
import io.github.abdulmmh.etm.security.JwtProperties;
import io.github.abdulmmh.etm.security.JwtService;
import io.github.abdulmmh.etm.security.SecurityConfiguration;
import io.github.abdulmmh.etm.security.SecurityErrorHandler;
import io.github.abdulmmh.etm.shared.error.ApiErrorWriter;
import io.github.abdulmmh.etm.shared.error.GlobalExceptionHandler;
import io.github.abdulmmh.etm.user.Role;
import io.github.abdulmmh.etm.user.User;
import io.github.abdulmmh.etm.user.UserController;
import io.github.abdulmmh.etm.user.UserRepository;
import io.github.abdulmmh.etm.user.UserService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.jwt.issuer=employee-task-manager",
    "app.jwt.expiration-seconds=1800",
    "app.cors.allowed-origins=http://localhost:4200"
})
@Import({SecurityConfiguration.class, JwtConfiguration.class, DatabaseUserDetailsService.class,
    DatabaseJwtAuthenticationConverter.class, JwtService.class, AuthService.class, UserService.class,
    GlobalExceptionHandler.class, SecurityErrorHandler.class, ApiErrorWriter.class})
class AuthSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private User admin;
    private User employee;

    @BeforeEach
    void setUp() {
        admin = user(1L, "admin@demo.com", "Demo Admin", Role.ADMIN, "Admin@123");
        employee = user(3L, "emp1@demo.com", "Employee One", Role.EMPLOYEE, "Emp@123");
        var manager = new User();
        ReflectionTestUtils.setField(manager, "id", 2L);
        manager.setRole(Role.MANAGER);
        employee.setManager(manager);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(3L)).thenReturn(Optional.of(employee));
        when(userRepository.findByEmailIgnoreCase("admin@demo.com")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase("emp1@demo.com")).thenReturn(Optional.of(employee));
    }

    @Test
    void loginReturnsTokenRoleAndNameWithoutPasswordOrSessionCookie() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"admin@demo.com","password":"Admin@123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.name").value("Demo Admin"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"admin@demo.com","password":"WrongPassword"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void unknownEmailUsesSameCredentialError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"missing@demo.com","password":"WrongPassword"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        employee.setActive(false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"emp1@demo.com","password":"Emp@123"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsIdentifyFieldsWithoutEchoingInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invalid-email","password":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    void malformedJsonReturnsStructuredBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void requiresBearerTokenForProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/users/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        mvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void adminCanReadUserDtoWithoutExposingPassword() throws Exception {
        mvc.perform(get("/api/users/3").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.managerId").value(2))
            .andExpect(jsonPath("$.role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void employeeCannotAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        verify(userRepository, never()).findById(1L);
    }

    @Test
    void managerCannotAccessAdminEndpoint() throws Exception {
        var manager = user(2L, "manager@demo.com", "Demo Manager", Role.MANAGER, "Manager@123");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        mvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager)))
            .andExpect(status().isForbidden());
    }

    @Test
    void oldTokenLosesAdminAuthorityAfterDatabaseRoleChanges() throws Exception {
        String token = jwtService.issue(admin);
        mvc.perform(get("/api/users/3").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        admin.setRole(Role.EMPLOYEE);

        mvc.perform(get("/api/users/3").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void oldTokenStopsWorkingAfterDeactivation() throws Exception {
        String token = jwtService.issue(admin);
        admin.setActive(false);

        mvc.perform(get("/api/users/3").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForMissingAccount() throws Exception {
        var missing = user(99L, "missing@demo.com", "Missing User", Role.ADMIN, "Admin@123");

        mvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(missing)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredSignedToken() throws Exception {
        var claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer()).subject("1")
            .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60)).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mvc.perform(get("/api/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseOutageDuringTokenAuthenticationReturnsServiceUnavailable() throws Exception {
        String token = jwtService.issue(admin);
        when(userRepository.findById(1L)).thenThrow(new DataAccessResourceFailureException("Unavailable"));

        mvc.perform(get("/api/users/3").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void absentUserReturnsNotFoundAfterAdminAuthorization() throws Exception {
        mvc.perform(get("/api/users/999").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownEndpointKeepsNotFoundStatus() throws Exception {
        mvc.perform(get("/api/not-implemented").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void permitsConfiguredCorsPreflightWithoutLogin() throws Exception {
        mvc.perform(options("/api/users/1")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void rejectsUnconfiguredCorsOrigin() throws Exception {
        mvc.perform(options("/api/users/1")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void methodAuthorizationAlsoProtectsCallsOutsideHttpController() {
        assertThatThrownBy(() -> userService.getUser(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    private User user(long id, String email, String name, Role role, String password) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(password));
        return user;
    }
}
