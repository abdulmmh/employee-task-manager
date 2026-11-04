package io.github.abdulmmh.etm.shared.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import io.github.abdulmmh.etm.auth.controller.AuthController;
import io.github.abdulmmh.etm.auth.dto.LoginRequest;
import io.github.abdulmmh.etm.auth.service.AuthService;
import io.github.abdulmmh.etm.user.service.UserService;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    @Mock private AuthService authService;
    @Mock private UserService userService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, userService))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void transactionConnectionFailureReturns503WithoutExposingInternalDetails() throws Exception {
        loginFailure(new CannotCreateTransactionException("Could not open JPA EntityManager: private-host",
            new SQLTransientConnectionException("private-database-details")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("Data service is temporarily unavailable. Please try again shortly."))
            .andExpect(jsonPath("$.fieldErrors").isEmpty())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(not(containsString("private-"))))
            .andExpect(content().string(not(containsString("EntityManager"))));
    }

    @Test
    void incorrectCredentialsStillReturn401() throws Exception {
        loginFailure(new BadCredentialsException("private credential diagnostics"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void unrelatedProgrammingFailureStillReturns500() throws Exception {
        loginFailure(new IllegalStateException("private implementation details"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
            .andExpect(content().string(not(containsString("private implementation"))));
    }

    private ResultActions loginFailure(RuntimeException failure) throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(failure);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"admin@demo.com\",\"password\":\"Admin@123\"}"));
    }
}
