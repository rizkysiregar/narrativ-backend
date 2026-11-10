package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.ReportEntity;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportReason;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;

import java.time.Instant;
import java.util.List;

public record AdminReportDTO(
        String id,
        String reporterId,
        String reporterUsername,
        ReportTargetType targetType,
        String targetId,
        ReportReason reason,
        String description,
        ReportStatus status,
        Instant createdAt,
        String resolvedBy,
        Instant resolvedAt,
        ReportAction resolutionAction,
        String resolutionNote,
        // only in the detail view
        Target target,
        Counts counts
) {
    /**
     * What the report points at, as it is now. {@code exists} is false once the target was hard deleted;
     * the other fields then stay empty.
     */
    public record Target(
            boolean exists,
            String authorId,
            String authorUsername,
            String text,
            List<AdminPostDTO.Media> media,
            String moderationStatus,
            String userStatus,
            boolean deletedByOwner,
            String postId
    ) {
        public static Target missing() {
            return new Target(false, null, null, null, List.of(), null, null, false, null);
        }
    }

    /** How many reports in total, and how many still open, point at the same target. */
    public record Counts(long total, long open) {}

    public static AdminReportDTO from(ReportEntity report, String reporterUsername) {
        return from(report, reporterUsername, null, null);
    }

    public static AdminReportDTO from(ReportEntity report, String reporterUsername, Target target, Counts counts) {
        return new AdminReportDTO(
                report.getId().toString(),
                report.getReporterId().toString(),
                reporterUsername,
                report.getTargetType(),
                report.getTargetId().toString(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedBy() == null ? null : report.getResolvedBy().toString(),
                report.getResolvedAt(),
                report.getResolutionAction(),
                report.getResolutionNote(),
                target,
                counts
        );
    }
}
