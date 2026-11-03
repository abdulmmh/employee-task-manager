package io.github.abdulmmh.etm.report.renderer;

import io.github.abdulmmh.etm.report.dto.TaskReportRow;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class TaskPdfRenderer {
    private static final float WIDTH = PDRectangle.A4.getHeight();
    private static final float HEIGHT = PDRectangle.A4.getWidth();
    private static final float MARGIN = 32;
    private static final float FONT_SIZE = 9;
    private static final float LINE_HEIGHT = 12;
    private static final float[] COLUMNS = {38, 235, 115, 115, 100, 66, 108};
    private static final String[] HEADERS = {"ID", "Title", "Assignee", "Assigned by", "Status", "Priority", "Deadline"};
    private static final Color INK = new Color(25, 42, 66);
    private static final Color ACCENT = new Color(20, 105, 115);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
        .withZone(ZoneId.of("UTC"));

    public byte[] render(String scope, String generatedBy, Instant generatedAt,
            List<TaskReportRow> rows) throws IOException {
        // Fonts and streams belong to this request; no mutable rendering state is shared.
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var info = document.getDocumentInformation();
            info.setTitle("Employee & Task Management System - Task Report");
            info.setAuthor("Employee & Task Management System");
            PDPage page = newPage(document);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                float y = header(stream, regular, bold, scope, generatedBy, generatedAt, rows.size());
                if (rows.isEmpty()) {
                    text(stream, regular, 11, MARGIN + 8, y - 23, "No tasks found for this scope.", INK);
                }
                int index = 0;
                for (TaskReportRow row : rows) {
                    String[] values = {row.id().toString(), row.title(), row.assigneeName(), row.assignerName(),
                        row.status().name().replace('_', ' '), row.priority().name(),
                        row.deadline() == null ? "-" : row.deadline().toString()};
                    List<List<String>> cells = new ArrayList<>();
                    int lineCount = 1;
                    for (int column = 0; column < COLUMNS.length; column++) {
                        var lines = wrap(regular, values[column], COLUMNS[column] - 14);
                        cells.add(lines);
                        lineCount = Math.max(lineCount, lines.size());
                    }
                    // Split unusually long cells across pages rather than clipping them.
                    int offset = 0;
                    while (offset < lineCount) {
                        int capacity = (int) ((y - 54 - 12) / LINE_HEIGHT);
                        if (capacity < 1) {
                            stream.close();
                            page = newPage(document);
                            stream = new PDPageContentStream(document, page);
                            y = header(stream, regular, bold, scope, generatedBy, generatedAt, rows.size());
                            continue;
                        }
                        int count = Math.min(lineCount - offset, capacity);
                        // Keep a normal row together if it can fit on a fresh page.
                        if (offset == 0 && count < lineCount && lineCount <= 28 && y < HEIGHT - 160) {
                            stream.close();
                            page = newPage(document);
                            stream = new PDPageContentStream(document, page);
                            y = header(stream, regular, bold, scope, generatedBy, generatedAt, rows.size());
                            continue;
                        }
                        float height = count * LINE_HEIGHT + 12;
                        fill(stream, MARGIN, y - height, WIDTH - 2 * MARGIN, height,
                            index % 2 == 0 ? new Color(242, 246, 249) : Color.WHITE);
                        float x = MARGIN;
                        for (int column = 0; column < COLUMNS.length; column++) {
                            var lines = cells.get(column);
                            for (int line = offset; line < Math.min(offset + count, lines.size()); line++) {
                                text(stream, regular, FONT_SIZE, x + 7,
                                    y - 15 - (line - offset) * LINE_HEIGHT, lines.get(line), INK);
                            }
                            x += COLUMNS[column];
                        }
                        y -= height;
                        offset += count;
                    }
                    index++;
                }
            } finally {
                stream.close();
            }
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try (var footer = new PDPageContentStream(document, document.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    text(footer, regular, 8, MARGIN, 28, "Confidential - authorized use only", INK);
                    text(footer, regular, 8, WIDTH - 110, 28,
                        "Page " + (i + 1) + " of " + document.getNumberOfPages(), INK);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private PDPage newPage(PDDocument document) {
        var page = new PDPage(new PDRectangle(WIDTH, HEIGHT));
        document.addPage(page);
        return page;
    }

    private float header(PDPageContentStream stream, PDFont regular, PDFont bold, String scope,
            String name, Instant at, int total) throws IOException {
        fill(stream, 0, HEIGHT - 7, WIDTH, 7, ACCENT);
        text(stream, bold, 18, MARGIN, HEIGHT - 42, "Employee & Task Management System", INK);
        text(stream, bold, 12, MARGIN, HEIGHT - 64, "TASK REPORT", ACCENT);
        text(stream, regular, 9, MARGIN, HEIGHT - 84, "Scope: " + scope, INK);
        // User names have a bounded column too, so long names cannot cross the timestamp.
        var names = wrap(regular, "Generated by: " + name, 370);
        float y = HEIGHT - 101;
        for (String line : names) {
            text(stream, regular, 9, MARGIN, y, line, INK);
            y -= LINE_HEIGHT;
        }
        text(stream, regular, 9, WIDTH - 240, HEIGHT - 84, "Generated: " + TIME.format(at), INK);
        text(stream, bold, 9, WIDTH - 240, HEIGHT - 101, "Total tasks: " + total, INK);
        y -= 4;
        fill(stream, MARGIN, y - 25, WIDTH - 2 * MARGIN, 25, INK);
        float x = MARGIN;
        for (int i = 0; i < HEADERS.length; i++) {
            text(stream, bold, 9, x + 7, y - 16, HEADERS[i], Color.WHITE);
            x += COLUMNS[i];
        }
        return y - 25;
    }

    private List<String> wrap(PDFont font, String value, float width) throws IOException {
        String clean = printable(font, value == null ? "-" : value).replaceAll("\\s+", " ").strip();
        var result = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : clean.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * FONT_SIZE > width) {
                result.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            // Normal words stay together; only a word wider than the cell is split.
            for (int index = 0; index < word.length(); index++) {
                char next = word.charAt(index);
                if (!line.isEmpty() && font.getStringWidth(line.toString() + next) / 1000 * FONT_SIZE > width) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                line.append(next);
            }
        }
        if (!line.isEmpty() || result.isEmpty()) result.add(line.toString());
        return result;
    }

    private String printable(PDFont font, String value) throws IOException {
        var result = new StringBuilder();
        for (int cp : value.codePoints().toArray()) {
            if (Character.isISOControl(cp)) {
                result.append(' ');
                continue;
            }
            String character = new String(Character.toChars(cp));
            try {
                font.getStringWidth(character);
                result.append(character);
            } catch (IllegalArgumentException unsupportedGlyph) {
                // Standard PDF fonts cover English/WinAnsi, not Bengali shaping or emoji.
                result.append('?');
            }
        }
        return result.toString();
    }

    private void text(PDPageContentStream stream, PDFont font, float size, float x, float y,
            String value, Color color) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(printable(font, value));
        stream.endText();
    }

    private void fill(PDPageContentStream stream, float x, float y, float width, float height,
            Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.addRect(x, y, width, height);
        stream.fill();
    }
}
