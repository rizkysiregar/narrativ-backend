package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminReportDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.ReportEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportReason;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.model.request.AdminReportSearchRequest;
import com.rizkysiregar.feedzmore.model.request.BanUserRequest;
import com.rizkysiregar.feedzmore.model.request.CreateReportRequest;
import com.rizkysiregar.feedzmore.model.request.DismissReportRequest;
import com.rizkysiregar.feedzmore.model.request.ModerationReasonRequest;
import com.rizkysiregar.feedzmore.model.request.ResolveReportRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.ReportRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reports from users (post, comment or user) and the admin queue that works them off.
 * <p>
 * Resolving a report with an action runs that action through the same services as the direct admin endpoints,
 * in the same transaction: if the takedown, ban or hard delete is refused, the report stays open.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private static final int NOTE_MIN = 5;
    private static final int NOTE_MAX = 500;

    private static final Map<ReportTargetType, Set<ReportAction>> ALLOWED_ACTIONS = Map.of(
            ReportTargetType.POST, Set.of(ReportAction.NONE, ReportAction.TAKEDOWN, ReportAction.BAN, ReportAction.HARD_DELETE),
            ReportTargetType.COMMENT, Set.of(ReportAction.NONE, ReportAction.TAKEDOWN, ReportAction.BAN),
            ReportTargetType.USER, Set.of(ReportAction.NONE, ReportAction.BAN));

    private final ReportRepository reportRepository;
    private final PostCrudRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AdminContentModerationService moderationService;
    private final UserManagementService userManagementService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    // ------------------------------------------------------------------ user side

    @Transactional
    public Operation createReport(UUID reporterId, CreateReportRequest request) {
        UUID targetId = parseUuid(request.targetId());
        String description = blankToNull(request.description());
        if (request.reason() == ReportReason.OTHER && description == null) {
            throw new BusinessException(GlobalMessage.REPORT_DESCRIPTION_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        UUID ownerId = reportableOwnerId(request.targetType(), targetId);
        if (ownerId.equals(reporterId)) {
            throw new BusinessException(GlobalMessage.CANNOT_REPORT_OWN, HttpStatus.BAD_REQUEST);
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.targetType(), targetId)) {
            throw new BusinessException(GlobalMessage.REPORT_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        try {
            reportRepository.saveAndFlush(ReportEntity.builder()
                    .reporterId(reporterId)
                    .targetType(request.targetType())
                    .targetId(targetId)
                    .reason(request.reason())
                    .description(description)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // two requests of the same user at once: the unique constraint is the real guard
            throw new BusinessException(GlobalMessage.REPORT_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        return Operation.CREATED;
    }

    // what a user may report: something other users can still see, that is not their own
    private UUID reportableOwnerId(ReportTargetType type, UUID targetId) {
        return switch (type) {
            case POST -> {
                PostEntity post = postRepository.findById(targetId)
                        .filter(found -> !found.isDeleted() && found.getModerationStatus() == ModerationStatus.ACTIVE
                                && isActive(found.getUser()))
                        .orElseThrow(this::notFound);
                yield post.getUser().getId();
            }
            case COMMENT -> {
                CommentEntity comment = commentRepository.findById(targetId)
                        .filter(found -> found.getModerationStatus() == ModerationStatus.ACTIVE && isActive(found.getUser())
                                && postRepository.existsInteractableById(found.getPost().getId()))
                        .orElseThrow(this::notFound);
                yield comment.getUser().getId();
            }
            case USER -> userRepository.findActiveById(targetId).map(UserEntity::getId).orElseThrow(this::notFound);
        };
    }

    // ----------------------------------------------------------------- admin side

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminReportDTO> search(AdminReportSearchRequest request, Pageable pageable) {
        ReportStatus status = parseEnum(ReportStatus.class, request.status());
        ReportTargetType targetType = parseEnum(ReportTargetType.class, request.targetType());
        ReportReason reason = parseEnum(ReportReason.class, request.reason());
        UUID reporterId = parseOptionalUuid(request.reporterId());
        UUID targetId = parseOptionalUuid(request.targetId());
        if (request.createdFrom() != null && request.createdTo() != null && request.createdFrom().isAfter(request.createdTo())) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        Instant from = startOf(request.createdFrom());
        Instant before = request.createdTo() == null ? null : startOf(request.createdTo().plusDays(1));

        Page<ReportEntity> page = reportRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (reporterId != null) {
                predicates.add(cb.equal(root.get("reporterId"), reporterId));
            }
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (before != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), before));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        Map<UUID, String> usernames = usernamesOf(page.getContent().stream().map(ReportEntity::getReporterId).collect(Collectors.toSet()));
        return new PaginatedResponse<>(
                page.getContent().stream()
                        .map(report -> AdminReportDTO.from(report, usernames.get(report.getReporterId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public AdminReportDTO getReport(String reportId) {
        ReportEntity report = findReport(parseUuid(reportId));
        AdminReportDTO.Counts counts = new AdminReportDTO.Counts(
                reportRepository.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()),
                reportRepository.countByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), ReportStatus.OPEN));
        return AdminReportDTO.from(report, usernameOf(report.getReporterId()), targetOf(report), counts);
    }

    @Transactional
    public AdminReportDTO resolve(String reportId, ResolveReportRequest request, UUID adminId) {
        ReportEntity report = findReport(parseUuid(reportId));
        requireOpen(report);

        ReportAction action = request.action();
        if (!ALLOWED_ACTIONS.get(report.getTargetType()).contains(action)) {
            throw new BusinessException(GlobalMessage.REPORT_ACTION_NOT_APPLICABLE, HttpStatus.BAD_REQUEST);
        }
        String note = blankToNull(request.note());
        if (action != ReportAction.NONE && (note == null || note.length() < NOTE_MIN || note.length() > NOTE_MAX)) {
            throw new BusinessException(GlobalMessage.REPORT_NOTE_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        // close this report first, with the real note: the action below then only auto-closes the other open
        // reports of the same target. If the action is refused the whole transaction rolls back and it stays open.
        close(report, ReportStatus.RESOLVED, adminId, action, note);
        reportRepository.saveAndFlush(report);

        if (action != ReportAction.NONE) {
            perform(report, action, note, adminId);
        }

        auditLogService.record(AdminAuditAction.REPORT_RESOLVE, AuditTargetType.REPORT, report.getId().toString(),
                note, AuditLogService.metadata(
                        "action", action.name(),
                        "targetType", report.getTargetType().name(),
                        "targetId", report.getTargetId().toString()));

        return AdminReportDTO.from(report, usernameOf(report.getReporterId()));
    }

    @Transactional
    public AdminReportDTO dismiss(String reportId, DismissReportRequest request, UUID adminId) {
        ReportEntity report = findReport(parseUuid(reportId));
        requireOpen(report);

        String note = request == null ? null : blankToNull(request.note());
        close(report, ReportStatus.DISMISSED, adminId, null, note);
        reportRepository.saveAndFlush(report);

        auditLogService.record(AdminAuditAction.REPORT_DISMISS, AuditTargetType.REPORT, report.getId().toString(),
                note, AuditLogService.metadata(
                        "targetType", report.getTargetType().name(),
                        "targetId", report.getTargetId().toString()));

        return AdminReportDTO.from(report, usernameOf(report.getReporterId()));
    }

    private void perform(ReportEntity report, ReportAction action, String note, UUID adminId) {
        String targetId = report.getTargetId().toString();
        ModerationReasonRequest reason = new ModerationReasonRequest(note);
        switch (action) {
            case TAKEDOWN -> {
                if (report.getTargetType() == ReportTargetType.POST) {
                    moderationService.takedownPost(targetId, reason, adminId);
                } else {
                    moderationService.takedownComment(targetId, reason, adminId);
                }
            }
            case HARD_DELETE -> moderationService.hardDeletePost(targetId, reason, adminId);
            case BAN -> userManagementService.banUserForAdmin(
                    ownerIdOf(report).toString(), new BanUserRequest(note), adminId);
            default -> throw new IllegalStateException("No target action for " + action);
        }
    }

    // the user an action against the target of the report lands on
    private UUID ownerIdOf(ReportEntity report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId()).map(post -> post.getUser().getId()).orElseThrow(this::notFound);
            case COMMENT -> commentRepository.findById(report.getTargetId()).map(comment -> comment.getUser().getId()).orElseThrow(this::notFound);
            case USER -> report.getTargetId();
        };
    }

    private void close(ReportEntity report, ReportStatus status, UUID adminId, ReportAction action, String note) {
        report.setStatus(status);
        report.setResolvedBy(adminId);
        report.setResolvedAt(Instant.now());
        report.setResolutionAction(action);
        report.setResolutionNote(note);
    }

    private AdminReportDTO.Target targetOf(ReportEntity report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(post -> new AdminReportDTO.Target(true,
                            post.getUser().getId().toString(), post.getUser().getUsername(), post.getCaption(),
                            post.isDeleted() ? null : presign(post.getMediaUrl()),
                            post.isDeleted() || post.getThumbnailUrl() == null ? null : presign(post.getThumbnailUrl()),
                            post.getModerationStatus().name(), post.getUser().getStatus().name(), post.isDeleted(), null))
                    .orElseGet(AdminReportDTO.Target::missing);
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(comment -> new AdminReportDTO.Target(true,
                            comment.getUser().getId().toString(), comment.getUser().getUsername(), comment.getContent(),
                            null, null, comment.getModerationStatus().name(), comment.getUser().getStatus().name(), false,
                            comment.getPost().getId().toString()))
                    .orElseGet(AdminReportDTO.Target::missing);
            case USER -> userRepository.findById(report.getTargetId())
                    .map(user -> new AdminReportDTO.Target(true,
                            user.getId().toString(), user.getUsername(), null, null, null,
                            null, user.getStatus().name(), user.isDeleted(), null))
                    .orElseGet(AdminReportDTO.Target::missing);
        };
    }

    // ------------------------------------------------------------------- helpers

    private boolean isActive(UserEntity user) {
        return !user.isDeleted() && user.getStatus() == UserStatus.ACTIVE;
    }

    private BusinessException notFound() {
        return new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    private ReportEntity findReport(UUID id) {
        return reportRepository.findById(id).orElseThrow(this::notFound);
    }

    private void requireOpen(ReportEntity report) {
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessException(GlobalMessage.REPORT_ALREADY_CLOSED, HttpStatus.CONFLICT);
        }
    }

    private String presign(String objectKey) {
        try {
            return fileStorageService.getPresignUrl(objectKey);
        } catch (Exception e) {
            log.warn("ADMIN.REPORT.PRESIGN.SKIPPED objectKey={} reason={}", objectKey, e.getMessage());
            return null;
        }
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).map(UserEntity::getUsername).orElse(null);
    }

    private Map<UUID, String> usernamesOf(Set<UUID> userIds) {
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> usernames.put(user.getId(), user.getUsername()));
        return usernames;
    }

    private Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(Constants.ADMIN_ZONE).toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    private UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : parseUuid(value);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
