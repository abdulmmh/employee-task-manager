package io.github.abdulmmh.etm.report.service;

import io.github.abdulmmh.etm.report.dto.TaskReportRow;
import io.github.abdulmmh.etm.report.renderer.TaskPdfRenderer;
import io.github.abdulmmh.etm.task.repository.TaskRepository;
import io.github.abdulmmh.etm.user.enums.Role;
import io.github.abdulmmh.etm.user.repository.UserRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskReportService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskPdfRenderer renderer;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional(readOnly = true)
    public byte[] download(long actorId) throws IOException {
        var actor = userRepository.findById(actorId).filter(user -> user.isActive())
            .orElseThrow(() -> new AccessDeniedException("An active account is required."));
        List<TaskReportRow> rows;
        String scope;
        if (actor.getRole() == Role.ADMIN) {
            rows = taskRepository.findAllReportRows();
            scope = "All tasks";
        } else if (actor.getRole() == Role.MANAGER) {
            rows = taskRepository.findTeamReportRows(actorId, Role.EMPLOYEE);
            scope = "Own team - current employees, all assigners";
        } else {
            throw new AccessDeniedException("Only administrators and managers can download reports.");
        }
        return renderer.render(scope, actor.getFullName(), Instant.now(), rows);
    }
}
