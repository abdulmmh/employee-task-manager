package io.github.abdulmmh.etm.shared.error;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatusCode;

public record ApiError(Instant timestamp, int status, String message, Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatusCode status, String message) {
        return of(status, message, Map.of());
    }

    public static ApiError of(HttpStatusCode status, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), message, fieldErrors);
    }
}
