package io.github.abdulmmh.etm.report.renderer;

import static org.assertj.core.api.Assertions.*;

import io.github.abdulmmh.etm.report.dto.TaskReportRow;
import io.github.abdulmmh.etm.task.enums.TaskPriority;
import io.github.abdulmmh.etm.task.enums.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TaskPdfRendererTest {
    private final TaskPdfRenderer renderer = new TaskPdfRenderer();
    private final Instant at = Instant.parse("2026-11-02T12:00:00Z");

    @Test void createsReadablePdfWithMetadataColumnsAndTaskDetails() throws Exception {
        byte[] pdf = renderer.render("All tasks", "Demo Admin", at, List.of(row(1, "Prepare summary")));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getDocumentInformation().getTitle()).contains("Task Report");
            assertThat(text).contains("TASK REPORT", "All tasks", "Demo Admin", "2026-11-02 12:00 UTC",
                "Total tasks: 1", "Assigned by", "Prepare summary", "Employee One", "Manager One",
                "IN PROGRESS", "HIGH", "2026-11-04", "Page 1 of 1");
        }
    }

    @Test void emptyReportHasExplicitMessage() throws Exception {
        try (var document = Loader.loadPDF(renderer.render("Own team", "Manager", at, List.of()))) {
            assertThat(new PDFTextStripper().getText(document)).contains("Total tasks: 0", "No tasks found");
        }
    }

    @Test void multiplePagesRepeatHeadersAndKeepLastTaskAndPageNumbers() throws Exception {
        var rows = new ArrayList<TaskReportRow>();
        for (long i = 1; i <= 60; i++) rows.add(row(i, "Task " + i + " - Prepare delivery checklist"));
        byte[] pdf = renderer.render("All tasks", "Demo Admin", at, rows);
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Task 60", "Page 1 of " + document.getNumberOfPages());
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                var stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                assertThat(stripper.getText(document)).contains("TASK REPORT", "Assignee", "Deadline",
                    "Page " + i + " of " + document.getNumberOfPages());
            }
        }
        // User-run test creates an ignored preview for subsequent visual review.
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "report-preview.pdf"), pdf);
    }

    @Test void veryLongCellContinuesAcrossPagesWithoutLosingTail() throws Exception {
        byte[] pdf = renderer.render("All tasks", "Demo Admin", at,
            List.of(row(1, "Long text ".repeat(400) + "ENDMARKER")));
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(document)).contains("ENDMARKER");
        }
    }

    @Test void missingDeadlineAndUnsupportedGlyphsDoNotCrashExport() throws Exception {
        var row = new TaskReportRow(1L, "Task \uD83D\uDE00", "Employee", "Admin",
            TaskStatus.PENDING, TaskPriority.LOW, null);
        try (var document = Loader.loadPDF(renderer.render("All tasks", "Admin", at, List.of(row)))) {
            assertThat(new PDFTextStripper().getText(document)).contains("Task ?", "PENDING", "LOW");
        }
    }

    private TaskReportRow row(long id, String title) {
        return new TaskReportRow(id, title, "Employee One", "Manager One",
            TaskStatus.IN_PROGRESS, TaskPriority.HIGH, LocalDate.of(2026, 11, 4));
    }
}
