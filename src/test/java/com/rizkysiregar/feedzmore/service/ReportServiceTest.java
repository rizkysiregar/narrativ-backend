package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminReportDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.ReportEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportReason;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.model.request.AdminReportSearchRequest;
import com.rizkysiregar.feedzmore.model.request.BanUserRequest;
import com.rizkysiregar.feedzmore.model.request.CreateReportRequest;
import com.rizkysiregar.feedzmore.model.request.DismissReportRequest;
import com.rizkysiregar.feedzmore.model.request.ModerationReasonRequest;
import com.rizkysiregar.feedzmore.model.request.ResolveReportRequest;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.PostMediaRepository;
import com.rizkysiregar.feedzmore.repository.ReportRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PostCrudRepository postRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminContentModerationService moderationService;
    @Mock
    private UserManagementService userManagementService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ReportService service;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private UserEntity user(String name) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(name);
        user.setRole(Role.USER);
        return user;
    }

    private PostEntity post(UserEntity author) {
        return PostEntity.builder().id(UUID.randomUUID()).user(author).caption("caption")
                .createdAt(LocalDateTime.now()).build();
    }

    private CommentEntity comment(UserEntity author, PostEntity post) {
        return CommentEntity.builder().id(UUID.randomUUID()).user(author).post(post).content("text").build();
    }

    private ReportEntity openReport(ReportTargetType type, UUID targetId) {
        return ReportEntity.builder().id(UUID.randomUUID()).reporterId(reporterId).targetType(type)
                .targetId(targetId).reason(ReportReason.SPAM).build();
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), exception.getMessage());
        assertEquals(status, exception.getHttpStatus());
    }

    // ----------------------------------------------------------------- creating

    @Test
    void createReport_savesAnOpenReportForAVisiblePostOfSomeoneElse() {
        PostEntity post = post(user("author"));
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        Operation result = service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.POST, post.getId().toString(), ReportReason.SPAM, "  buy now  "));

        assertEquals(Operation.CREATED, result);
        ArgumentCaptor<ReportEntity> saved = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertEquals(reporterId, saved.getValue().getReporterId());
        assertEquals(ReportTargetType.POST, saved.getValue().getTargetType());
        assertEquals(post.getId(), saved.getValue().getTargetId());
        assertEquals("buy now", saved.getValue().getDescription());
        assertEquals(ReportStatus.OPEN, saved.getValue().getStatus());
    }

    @Test
    void createReport_cannotReportYourselfOrYourOwnContent() {
        UserEntity me = new UserEntity();
        me.setId(reporterId);
        PostEntity mine = post(me);
        when(postRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        CommentEntity myComment = comment(me, post(user("other")));
        when(commentRepository.findById(myComment.getId())).thenReturn(Optional.of(myComment));
        when(postRepository.existsInteractableById(myComment.getPost().getId())).thenReturn(true);
        when(userRepository.findActiveById(reporterId)).thenReturn(Optional.of(me));

        assertFails(GlobalMessage.CANNOT_REPORT_OWN, HttpStatus.BAD_REQUEST, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.POST, mine.getId().toString(), ReportReason.SPAM, null)));
        assertFails(GlobalMessage.CANNOT_REPORT_OWN, HttpStatus.BAD_REQUEST, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.COMMENT, myComment.getId().toString(), ReportReason.SPAM, null)));
        assertFails(GlobalMessage.CANNOT_REPORT_OWN, HttpStatus.BAD_REQUEST, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.USER, reporterId.toString(), ReportReason.SPAM, null)));

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReport_targetsOtherUsersCannotSeeAreNotFound() {
        UserEntity banned = user("banned");
        banned.setStatus(UserStatus.BANNED);
        PostEntity deleted = post(user("a")); deleted.setDeleted(true);
        PostEntity takenDown = post(user("b")); takenDown.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        PostEntity ofBanned = post(banned);
        for (PostEntity p : new PostEntity[]{deleted, takenDown, ofBanned}) {
            when(postRepository.findById(p.getId())).thenReturn(Optional.of(p));
            assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.createReport(reporterId,
                    new CreateReportRequest(ReportTargetType.POST, p.getId().toString(), ReportReason.SPAM, null)));
        }

        CommentEntity onHiddenPost = comment(user("c"), post(user("d")));
        when(commentRepository.findById(onHiddenPost.getId())).thenReturn(Optional.of(onHiddenPost));
        when(postRepository.existsInteractableById(onHiddenPost.getPost().getId())).thenReturn(false);
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.COMMENT, onHiddenPost.getId().toString(), ReportReason.SPAM, null)));

        UUID bannedUserId = UUID.randomUUID();
        when(userRepository.findActiveById(bannedUserId)).thenReturn(Optional.empty());
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.USER, bannedUserId.toString(), ReportReason.SPAM, null)));

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReport_duplicatesAreConflictsAlsoWhenTheyRaceThroughTheUniqueConstraint() {
        PostEntity post = post(user("author"));
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        CreateReportRequest request = new CreateReportRequest(ReportTargetType.POST, post.getId().toString(), ReportReason.SPAM, null);

        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.POST, post.getId())).thenReturn(true);
        assertFails(GlobalMessage.REPORT_ALREADY_EXISTS, HttpStatus.CONFLICT, () -> service.createReport(reporterId, request));
        verify(reportRepository, never()).saveAndFlush(any());

        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, ReportTargetType.POST, post.getId())).thenReturn(false);
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenThrow(new DataIntegrityViolationException("uk_reports_reporter_target"));
        assertFails(GlobalMessage.REPORT_ALREADY_EXISTS, HttpStatus.CONFLICT, () -> service.createReport(reporterId, request));
    }

    @Test
    void createReport_otherNeedsADescriptionAndABadIdIsBadRequest() {
        assertFails(GlobalMessage.REPORT_DESCRIPTION_REQUIRED, HttpStatus.BAD_REQUEST, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.USER, UUID.randomUUID().toString(), ReportReason.OTHER, "   ")));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.createReport(reporterId,
                new CreateReportRequest(ReportTargetType.POST, "not-a-uuid", ReportReason.SPAM, null)));
        verifyNoInteractions(postRepository, userRepository);
    }

    // ---------------------------------------------------------------- resolving

    @Test
    void resolve_none_closesTheReportWithoutTouchingTheTarget() {
        ReportEntity report = openReport(ReportTargetType.POST, UUID.randomUUID());
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        AdminReportDTO result = service.resolve(report.getId().toString(), new ResolveReportRequest(ReportAction.NONE, null), adminId);

        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        assertEquals(ReportAction.NONE, report.getResolutionAction());
        assertEquals(adminId, report.getResolvedBy());
        assertNotNull(report.getResolvedAt());
        assertEquals(ReportStatus.RESOLVED, result.status());
        verifyNoInteractions(moderationService, userManagementService);
        verify(auditLogService).record(eq(AdminAuditAction.REPORT_RESOLVE), eq(AuditTargetType.REPORT),
                eq(report.getId().toString()), isNull(), any());
    }

    @Test
    void resolve_takedownClosesTheReportBeforeRunningTheActionWithTheNoteAsReason() {
        UUID postId = UUID.randomUUID();
        ReportEntity report = openReport(ReportTargetType.POST, postId);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        service.resolve(report.getId().toString(), new ResolveReportRequest(ReportAction.TAKEDOWN, "  spam jelas  "), adminId);

        assertEquals("spam jelas", report.getResolutionNote());
        InOrder order = inOrder(reportRepository, moderationService, auditLogService);
        order.verify(reportRepository).saveAndFlush(report);
        order.verify(moderationService).takedownPost(postId.toString(), new ModerationReasonRequest("spam jelas"), adminId);
        order.verify(auditLogService).record(eq(AdminAuditAction.REPORT_RESOLVE), eq(AuditTargetType.REPORT), any(), eq("spam jelas"), any());
    }

    @Test
    void resolve_eachActionGoesToTheRightService() {
        UUID id = UUID.randomUUID();

        ReportEntity comment = openReport(ReportTargetType.COMMENT, id);
        when(reportRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        service.resolve(comment.getId().toString(), new ResolveReportRequest(ReportAction.TAKEDOWN, "kata kasar"), adminId);
        verify(moderationService).takedownComment(id.toString(), new ModerationReasonRequest("kata kasar"), adminId);

        ReportEntity hardDelete = openReport(ReportTargetType.POST, id);
        when(reportRepository.findById(hardDelete.getId())).thenReturn(Optional.of(hardDelete));
        service.resolve(hardDelete.getId().toString(), new ResolveReportRequest(ReportAction.HARD_DELETE, "konten ilegal"), adminId);
        verify(moderationService).hardDeletePost(id.toString(), new ModerationReasonRequest("konten ilegal"), adminId);

        ReportEntity banUser = openReport(ReportTargetType.USER, id);
        when(reportRepository.findById(banUser.getId())).thenReturn(Optional.of(banUser));
        service.resolve(banUser.getId().toString(), new ResolveReportRequest(ReportAction.BAN, "akun spam"), adminId);
        verify(userManagementService).banUserForAdmin(id.toString(), new BanUserRequest("akun spam"), adminId);
    }

    @Test
    void resolve_banOnAPostOrCommentReportBansItsAuthor() {
        UserEntity author = user("author");
        PostEntity post = post(author);
        ReportEntity report = openReport(ReportTargetType.POST, post.getId());
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        service.resolve(report.getId().toString(), new ResolveReportRequest(ReportAction.BAN, "pelanggar berulang"), adminId);
        verify(userManagementService).banUserForAdmin(author.getId().toString(), new BanUserRequest("pelanggar berulang"), adminId);

        CommentEntity comment = comment(author, post);
        ReportEntity commentReport = openReport(ReportTargetType.COMMENT, comment.getId());
        when(reportRepository.findById(commentReport.getId())).thenReturn(Optional.of(commentReport));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        service.resolve(commentReport.getId().toString(), new ResolveReportRequest(ReportAction.BAN, "pelanggar berulang"), adminId);
        verify(userManagementService, org.mockito.Mockito.times(2)).banUserForAdmin(author.getId().toString(), new BanUserRequest("pelanggar berulang"), adminId);
    }

    @Test
    void resolve_rejectsActionsThatDoNotFitTheTargetAndMissingNotes() {
        UUID id = UUID.randomUUID();
        ReportEntity userReport = openReport(ReportTargetType.USER, id);
        ReportEntity commentReport = openReport(ReportTargetType.COMMENT, id);
        ReportEntity postReport = openReport(ReportTargetType.POST, id);
        when(reportRepository.findById(userReport.getId())).thenReturn(Optional.of(userReport));
        when(reportRepository.findById(commentReport.getId())).thenReturn(Optional.of(commentReport));
        when(reportRepository.findById(postReport.getId())).thenReturn(Optional.of(postReport));

        assertFails(GlobalMessage.REPORT_ACTION_NOT_APPLICABLE, HttpStatus.BAD_REQUEST, () -> service.resolve(
                userReport.getId().toString(), new ResolveReportRequest(ReportAction.TAKEDOWN, "alasan cukup"), adminId));
        assertFails(GlobalMessage.REPORT_ACTION_NOT_APPLICABLE, HttpStatus.BAD_REQUEST, () -> service.resolve(
                commentReport.getId().toString(), new ResolveReportRequest(ReportAction.HARD_DELETE, "alasan cukup"), adminId));
        assertFails(GlobalMessage.REPORT_NOTE_REQUIRED, HttpStatus.BAD_REQUEST, () -> service.resolve(
                postReport.getId().toString(), new ResolveReportRequest(ReportAction.TAKEDOWN, null), adminId));
        assertFails(GlobalMessage.REPORT_NOTE_REQUIRED, HttpStatus.BAD_REQUEST, () -> service.resolve(
                postReport.getId().toString(), new ResolveReportRequest(ReportAction.TAKEDOWN, "abc"), adminId));

        assertEquals(ReportStatus.OPEN, postReport.getStatus());
        verify(reportRepository, never()).saveAndFlush(any());
        verifyNoInteractions(moderationService, userManagementService, auditLogService);
    }

    @Test
    void resolve_aClosedReportIsAConflictAndAnUnknownOneNotFound() {
        ReportEntity closed = openReport(ReportTargetType.POST, UUID.randomUUID());
        closed.setStatus(ReportStatus.DISMISSED);
        when(reportRepository.findById(closed.getId())).thenReturn(Optional.of(closed));
        assertFails(GlobalMessage.REPORT_ALREADY_CLOSED, HttpStatus.CONFLICT, () -> service.resolve(
                closed.getId().toString(), new ResolveReportRequest(ReportAction.NONE, null), adminId));

        UUID unknown = UUID.randomUUID();
        when(reportRepository.findById(unknown)).thenReturn(Optional.empty());
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.resolve(
                unknown.toString(), new ResolveReportRequest(ReportAction.NONE, null), adminId));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.resolve(
                "not-a-uuid", new ResolveReportRequest(ReportAction.NONE, null), adminId));
    }

    // ---------------------------------------------------------------- dismissing

    @Test
    void dismiss_closesWithoutActionAndAudits() {
        ReportEntity report = openReport(ReportTargetType.COMMENT, UUID.randomUUID());
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        service.dismiss(report.getId().toString(), new DismissReportRequest("tidak melanggar"), adminId);

        assertEquals(ReportStatus.DISMISSED, report.getStatus());
        assertNull(report.getResolutionAction());
        assertEquals("tidak melanggar", report.getResolutionNote());
        assertEquals(adminId, report.getResolvedBy());
        verifyNoInteractions(moderationService, userManagementService);
        verify(auditLogService).record(eq(AdminAuditAction.REPORT_DISMISS), eq(AuditTargetType.REPORT),
                eq(report.getId().toString()), eq("tidak melanggar"), any());
    }

    @Test
    void dismiss_worksWithoutABodyButNotOnAClosedReport() {
        ReportEntity report = openReport(ReportTargetType.USER, UUID.randomUUID());
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        service.dismiss(report.getId().toString(), null, adminId);
        assertEquals(ReportStatus.DISMISSED, report.getStatus());

        assertFails(GlobalMessage.REPORT_ALREADY_CLOSED, HttpStatus.CONFLICT,
                () -> service.dismiss(report.getId().toString(), null, adminId));
    }

    // -------------------------------------------------------------------- detail

    @Test
    void getReport_showsTheTargetAsItIsNowAndTheCounts() {
        UserEntity author = user("author");
        PostEntity post = post(author);
        post.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        ReportEntity report = openReport(ReportTargetType.POST, post.getId());
        UserEntity reporter = new UserEntity();
        reporter.setId(reporterId);
        reporter.setUsername("reporter");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.POST, post.getId())).thenReturn(3L);
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, post.getId(), ReportStatus.OPEN)).thenReturn(2L);

        AdminReportDTO result = service.getReport(report.getId().toString());

        assertEquals("reporter", result.reporterUsername());
        assertTrue(result.target().exists());
        assertEquals("author", result.target().authorUsername());
        assertEquals("caption", result.target().text());
        assertEquals("TAKEN_DOWN", result.target().moderationStatus());
        assertEquals(new AdminReportDTO.Counts(3, 2), result.counts());
    }

    @Test
    void getReport_ofAHardDeletedTargetSaysItIsGone() {
        UUID postId = UUID.randomUUID();
        ReportEntity report = openReport(ReportTargetType.POST, postId);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        AdminReportDTO result = service.getReport(report.getId().toString());

        assertFalse(result.target().exists());
        assertNull(result.target().text());
    }

    // ------------------------------------------------------------------- filters

    @Test
    void search_rejectsInvalidFilters() {
        PageRequest page = PageRequest.of(0, 20);

        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.search(
                new AdminReportSearchRequest("PENDING", null, null, null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.search(
                new AdminReportSearchRequest(null, "SHOP", null, null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.search(
                new AdminReportSearchRequest(null, null, "RUDE", null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.search(
                new AdminReportSearchRequest(null, null, null, "not-a-uuid", null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.search(
                new AdminReportSearchRequest(null, null, null, null, null, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7)), page));

        verifyNoInteractions(reportRepository);
    }
}
