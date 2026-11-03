package io.github.abdulmmh.etm.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import io.github.abdulmmh.etm.task.TaskRepository;
import io.github.abdulmmh.etm.user.Role;
import io.github.abdulmmh.etm.user.User;
import io.github.abdulmmh.etm.user.UserController;
import io.github.abdulmmh.etm.user.UserRepository;
import io.github.abdulmmh.etm.user.UserService;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

@WebMvcTest(controllers = {AuthController.class, UserController.class,
    io.github.abdulmmh.etm.task.TaskController.class,
    io.github.abdulmmh.etm.dashboard.DashboardController.class,
    io.github.abdulmmh.etm.report.TaskReportController.class,
    io.github.abdulmmh.etm.team.TeamController.class,
    io.github.abdulmmh.etm.health.HealthController.class}, properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.jwt.issuer=employee-task-manager",
    "app.jwt.expiration-seconds=1800",
    "app.cors.allowed-origins=http://localhost:4200"
})
@Import({SecurityConfiguration.class, JwtConfiguration.class, DatabaseUserDetailsService.class,
    DatabaseJwtAuthenticationConverter.class, JwtService.class, AuthService.class, UserService.class,
    GlobalExceptionHandler.class, SecurityErrorHandler.class, ApiErrorWriter.class,
    io.github.abdulmmh.etm.task.TaskService.class,
    io.github.abdulmmh.etm.dashboard.DashboardService.class,
    io.github.abdulmmh.etm.report.TaskReportService.class,
    io.github.abdulmmh.etm.report.TaskPdfRenderer.class,
    io.github.abdulmmh.etm.team.TeamService.class})
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

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private io.github.abdulmmh.etm.health.HealthService healthService;

    @Test
    void publicHealthReturnsOnlyUpWithoutAuthentication() throws Exception {
        when(healthService.databaseAvailable()).thenReturn(true);
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string("{\"status\":\"UP\"}"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(userRepository, taskRepository);
    }

    @Test
    void publicHealthReturns503WithoutDatabaseDetails() throws Exception {
        when(healthService.databaseAvailable()).thenReturn(false);
        mvc.perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string("{\"status\":\"DOWN\"}"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void healthPublicPermissionDoesNotAllowPost() throws Exception {
        mvc.perform(post("/api/health"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(healthService);
    }

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

    @Test
    void administratorCanListPaginatedUserDtos() throws Exception {
        var page = new PageImpl<>(List.of(admin, employee), PageRequest.of(0, 10), 2);
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), any(Pageable.class)))
            .thenReturn(page);

        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void administratorCanCreateEmployeeWithBcryptPassword() throws Exception {
        allowUserWrites();

        mvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"New Employee","email":"new@demo.com","password":"Emp@123",
                     "role":"EMPLOYEE","managerId":2}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/users/13"))
            .andExpect(jsonPath("$.managerId").value(2))
            .andExpect(jsonPath("$.password").doesNotExist());

        var saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        org.assertj.core.api.Assertions.assertThat(
            passwordEncoder.matches("Emp@123", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void administratorCanRegisterAdditionalAdminThroughAlias() throws Exception {
        allowUserWrites();

        mvc.perform(post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newAdminJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registrationIsNotPublic() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(newAdminJson()))
            .andExpect(status().isUnauthorized());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void employeeCannotRegisterAdministrator() throws Exception {
        mvc.perform(post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee))
                .contentType(MediaType.APPLICATION_JSON).content(newAdminJson()))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createRequestValidationRejectsMissingFields() throws Exception {
        mvc.perform(post("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        allowUserWrites();
        when(userRepository.existsByEmailIgnoreCase("admin2@demo.com")).thenReturn(true);

        mvc.perform(post("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newAdminJson()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void databaseConstraintRaceAlsoReturnsConflict() throws Exception {
        allowUserWrites();
        doThrow(new DataIntegrityViolationException("Unique constraint"))
            .when(userRepository).saveAndFlush(any(User.class));

        mvc.perform(post("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newAdminJson()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Request conflicts with existing data."));
    }

    @Test
    void editWithoutPasswordPreservesExistingCredential() throws Exception {
        allowUserWrites();
        String previousHash = employee.getPassword();

        mvc.perform(put("/api/users/3")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Updated Employee","email":"emp1@demo.com",
                     "role":"EMPLOYEE","managerId":2,"active":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Updated Employee"))
            .andExpect(jsonPath("$.password").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(employee.getPassword()).isEqualTo(previousHash);
    }

    @Test
    void deleteDeactivatesInsteadOfRemovingUser() throws Exception {
        allowUserWrites();

        mvc.perform(delete("/api/users/3")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(employee.isActive()).isFalse();
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteLastActiveAdminReturnsConflict() throws Exception {
        allowUserWrites();

        mvc.perform(delete("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));

        org.assertj.core.api.Assertions.assertThat(admin.isActive()).isTrue();
    }

    private void allowUserWrites() {
        when(userRepository.findActiveAdminsForUpdate(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(employee.getManager()));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(employee));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 13L);
            }
            return saved;
        });
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void creationAlsoRequiresAdminAtServiceBoundary() {
        var request = new io.github.abdulmmh.etm.user.CreateUserRequest(
            "Forbidden Admin", "forbidden@demo.com", "Admin@123", Role.ADMIN, null);

        assertThatThrownBy(() -> userService.createUser(1L, request))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    private String newAdminJson() {
        return """
            {"fullName":"Second Admin","email":"admin2@demo.com",
             "password":"Admin2@123","role":"ADMIN","managerId":null}
            """;
    }

    @Test
    void taskEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotCreateTask() throws Exception {
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee))
                .contentType(MediaType.APPLICATION_JSON).content(taskJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesTaskWithServerControlledStatusAndAssigner() throws Exception {
        allowUserWrites();
        when(taskRepository.saveAndFlush(any(io.github.abdulmmh.etm.task.Task.class))).thenAnswer(call -> {
            io.github.abdulmmh.etm.task.Task task = call.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 25L);
            return task;
        });
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content(taskJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/tasks/25"))
            .andExpect(jsonPath("$.assignedBy").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void invalidTaskBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"assignedTo\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.title").exists())
            .andExpect(jsonPath("$.fieldErrors.assignedTo").exists());
    }

    @Test
    void employeeCannotReadAnotherEmployeesTaskOverHttp() throws Exception {
        var task = fixtureTask();
        var other = new User();
        ReflectionTestUtils.setField(other, "id", 6L);
        task.setAssignedTo(other);
        mvc.perform(get("/api/tasks/20").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee)))
            .andExpect(status().isForbidden());
    }

    @Test
    void employeeCanStartOwnTaskOverHttp() throws Exception {
        fixtureTask();
        when(taskRepository.saveAndFlush(any(io.github.abdulmmh.etm.task.Task.class)))
            .thenAnswer(call -> call.getArgument(0));
        mvc.perform(put("/api/tasks/20/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void skippedTaskStatusReturns409OverHttp() throws Exception {
        fixtureTask();
        mvc.perform(put("/api/tasks/20/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void adminCannotUseEmployeeStatusEndpoint() throws Exception {
        mvc.perform(put("/api/tasks/20/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotDeleteTaskOverHttp() throws Exception {
        var manager = employee.getManager();
        manager.setActive(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        mvc.perform(delete("/api/tasks/20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminDeletesTaskOverHttp() throws Exception {
        fixtureTask();
        mvc.perform(delete("/api/tasks/20").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isNoContent());
        verify(taskRepository).delete(any(io.github.abdulmmh.etm.task.Task.class));
    }

    @Test
    void invalidTaskFilterReturns400() throws Exception {
        mvc.perform(get("/api/tasks?status=INVALID")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isBadRequest());
    }

    private io.github.abdulmmh.etm.task.Task fixtureTask() {
        var task = new io.github.abdulmmh.etm.task.Task();
        ReflectionTestUtils.setField(task, "id", 20L);
        task.setTitle("Test task");
        task.setAssignedBy(admin);
        task.setAssignedTo(employee);
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        return task;
    }

    private String taskJson() {
        return "{\"title\":\"Test task\",\"priority\":\"HIGH\",\"assignedTo\":3}";
    }

    @Test
    void employeeCannotReadTeamLookup() throws Exception {
        mvc.perform(get("/api/team/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee)))
            .andExpect(status().isForbidden());
    }

    @Test
    void teamLookupRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/team/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void managerTeamLookupReturnsOnlyIdNameDtos() throws Exception {
        var manager = employee.getManager();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(employee), PageRequest.of(0, 20), 1));
        mvc.perform(get("/api/team/employees?managerId=5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.content[0].name").value("Employee One"))
            .andExpect(jsonPath("$.content[0].email").doesNotExist())
            .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void reportRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/reports/tasks/pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotDownloadReport() throws Exception {
        mvc.perform(get("/api/reports/tasks/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee)))
            .andExpect(status().isForbidden());
        verify(taskRepository, never()).findAllReportRows();
    }

    @Test
    void adminDownloadsPdfAttachmentWithoutCaching() throws Exception {
        mvc.perform(get("/api/reports/tasks/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks-report.pdf\""))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                result.getResponse().getContentAsByteArray()).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        verify(taskRepository).findAllReportRows();
    }

    @Test
    void managerReportUsesCurrentTeamAndCannotOverrideScope() throws Exception {
        var manager = employee.getManager();
        manager.setFullName("Manager One");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        mvc.perform(get("/api/reports/tasks/pdf?managerId=5&role=ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager)))
            .andExpect(status().isOk());
        verify(taskRepository).findTeamReportRows(2L, Role.EMPLOYEE);
        verify(taskRepository, never()).findAllReportRows();
        verify(taskRepository, never()).findTeamReportRows(5L, Role.EMPLOYEE);
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDashboardShowsGlobalUserCounts() throws Exception {
        when(userRepository.count()).thenReturn(13L);
        when(userRepository.countByActiveTrue()).thenReturn(12L);
        mvc.perform(get("/api/dashboard/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.totalUsers").value(13))
            .andExpect(jsonPath("$.activeUsers").value(12))
            .andExpect(jsonPath("$.teamSize").doesNotExist());
    }

    @Test
    void managerDashboardOmitsGlobalUserCounts() throws Exception {
        var manager = employee.getManager();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(userRepository.countByManagerIdAndRoleAndActiveTrue(2L, Role.EMPLOYEE)).thenReturn(3L);
        mvc.perform(get("/api/dashboard/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.teamSize").value(3))
            .andExpect(jsonPath("$.totalUsers").doesNotExist())
            .andExpect(jsonPath("$.activeUsers").doesNotExist());
        verify(taskRepository).countByAssignerStatus(2L);
        verify(taskRepository, never()).countAllByStatus();
    }

    @Test
    void employeeDashboardIgnoresClientRoleAndUserIdAndReturnsOwnScope() throws Exception {
        mvc.perform(get("/api/dashboard/stats?role=ADMIN&userId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(employee)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("EMPLOYEE"))
            .andExpect(jsonPath("$.totalTasks").value(0))
            .andExpect(jsonPath("$.tasksByStatus.pending").value(0))
            .andExpect(jsonPath("$.tasksByStatus.inProgress").value(0))
            .andExpect(jsonPath("$.tasksByStatus.done").value(0))
            .andExpect(jsonPath("$.totalUsers").doesNotExist())
            .andExpect(jsonPath("$.teamSize").doesNotExist());
        verify(taskRepository).countByAssigneeStatus(3L);
        verify(taskRepository, never()).countAllByStatus();
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
