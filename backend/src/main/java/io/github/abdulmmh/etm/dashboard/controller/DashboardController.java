package io.github.abdulmmh.etm.dashboard.controller;

import io.github.abdulmmh.etm.dashboard.dto.DashboardResponse;
import io.github.abdulmmh.etm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public DashboardResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return dashboardService.stats(Long.parseLong(jwt.getSubject()));
    }
}
