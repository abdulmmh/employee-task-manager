package io.github.abdulmmh.etm.team.controller;

import io.github.abdulmmh.etm.shared.dto.PageResponse;
import io.github.abdulmmh.etm.team.dto.EmployeeOption;
import io.github.abdulmmh.etm.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService service;

    @GetMapping("/employees")
    public PageResponse<EmployeeOption> employees(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return service.employees(Long.parseLong(jwt.getSubject()), page, size, search);
    }
}
