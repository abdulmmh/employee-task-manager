package io.github.abdulmmh.etm.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final HealthService service;

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        boolean available = service.databaseAvailable();
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .cacheControl(CacheControl.noStore())
            .body(new HealthResponse(available ? "UP" : "DOWN"));
    }

    public record HealthResponse(String status) {}
}
