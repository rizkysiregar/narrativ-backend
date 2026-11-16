package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.enums.ReportAction;
import com.rizkysiregar.narrativ.model.enums.ReportTargetType;
import com.rizkysiregar.narrativ.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Closes the reports that are left open on a target an admin just acted on, so the queue never keeps reports
 * about content that is already gone, hidden or banned. Kept apart from {@link ReportService} because the
 * moderation services call it, while ReportService calls them.
 * <p>
 * Runs inside the transaction of the admin action it belongs to.
 */
@Service
@RequiredArgsConstructor
public class ReportClosingService {

    private final ReportRepository reportRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int closeOpenReports(ReportTargetType targetType, UUID targetId, UUID adminId, ReportAction action) {
        return reportRepository.closeOpenForTarget(targetType, targetId, adminId, Instant.now(), action, autoNote(action));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int closeOpenReportsOfCommentsOnPost(UUID postId, UUID adminId, ReportAction action) {
        return reportRepository.closeOpenForCommentsOfPost(postId, adminId, Instant.now(), action, autoNote(action));
    }

    static String autoNote(ReportAction action) {
        return "Ditutup otomatis: target ditindak langsung oleh admin (" + action + ").";
    }
}
