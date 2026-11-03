package io.github.abdulmmh.etm.health.controller;

import io.github.abdulmmh.etm.health.dto.HealthResponse;
import io.github.abdulmmh.etm.health.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    @RequestMapping(value = "/api/health", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headHealth() {
        boolean available = service.databaseAvailable();
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .cacheControl(CacheControl.noStore())
            .build();
    }
}
