package io.github.abdulmmh.etm.report.controller;

import io.github.abdulmmh.etm.report.service.TaskReportService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/tasks")
@RequiredArgsConstructor
public class TaskReportController {
    private final TaskReportService reportService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt) throws IOException {
        byte[] pdf = reportService.download(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("tasks-report.pdf").build().toString())
            .cacheControl(CacheControl.noStore())
            .contentLength(pdf.length)
            .body(pdf);
    }
}
