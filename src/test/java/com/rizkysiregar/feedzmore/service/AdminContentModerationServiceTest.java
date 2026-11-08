package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminCommentDTO;
import com.rizkysiregar.feedzmore.model.dto.AdminPostDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import com.rizkysiregar.feedzmore.model.request.AdminCommentSearchRequest;
import com.rizkysiregar.feedzmore.model.request.AdminPostSearchRequest;
import com.rizkysiregar.feedzmore.model.request.ModerationReasonRequest;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.LikeRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentModerationServiceTest {

    @Mock
    private PostCrudRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ContentModerationNotifier notifier;
    @Mock
    private ReportClosingService reportClosingService;

    @InjectMocks
    private AdminContentModerationService service;

    private final UUID adminId = UUID.randomUUID();
    private final ModerationReasonRequest reason = new ModerationReasonRequest("  melanggar aturan  ");

    private UserEntity author() {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        author.setUsername("author");
        return author;
    }

    private PostEntity post(UserEntity author) {
        return PostEntity.builder().id(UUID.randomUUID()).user(author).caption("a caption")
                .mediaUrl("media.jpg").thumbnailUrl("thumb.jpg").mediaType(MediaType.VIDEO)
                .createdAt(LocalDateTime.of(2026, 11, 7, 10, 0)).build();
    }

    private CommentEntity comment(UserEntity author, PostEntity post) {
        return CommentEntity.builder().id(UUID.randomUUID()).user(author).post(post).content("rude").build();
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), exception.getMessage());
        assertEquals(status, exception.getHttpStatus());
    }

    // --------------------------------------------------------- post takedown

    @Test
    void takedownPost_hidesItKeepsTheMediaAuditsAndNotifiesTheOwner() {
        PostEntity post = post(author());
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPostDTO result = service.takedownPost(post.getId().toString(), reason, adminId);

        assertEquals(ModerationStatus.TAKEN_DOWN, post.getModerationStatus());
        assertEquals("melanggar aturan", post.getModerationReason());
        assertEquals(adminId, post.getModeratedBy());
        assertNotNull(post.getModeratedAt());
        assertEquals(ModerationStatus.TAKEN_DOWN, result.moderationStatus());
        assertEquals("melanggar aturan", result.moderationReason());
        verify(fileStorageService, never()).deleteObject(any());
        verify(auditLogService).record(eq(AdminAuditAction.POST_TAKEDOWN), eq(AuditTargetType.POST),
                eq(post.getId().toString()), eq("melanggar aturan"), any());
        verify(notifier).postTakenDown(post.getUser().getId(), "melanggar aturan");
        verify(reportClosingService).closeOpenReports(ReportTargetType.POST, post.getId(), adminId, ReportAction.TAKEDOWN);
    }

    @Test
    void takedownPost_rejectsDeletedAlreadyTakenDownUnknownAndInvalidId() {
        PostEntity deleted = post(author());
        deleted.setDeleted(true);
        when(postRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        assertFails(GlobalMessage.POST_ALREADY_DELETED, HttpStatus.CONFLICT,
                () -> service.takedownPost(deleted.getId().toString(), reason, adminId));

        PostEntity taken = post(author());
        taken.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        when(postRepository.findById(taken.getId())).thenReturn(Optional.of(taken));
        assertFails(GlobalMessage.CONTENT_ALREADY_TAKEN_DOWN, HttpStatus.CONFLICT,
                () -> service.takedownPost(taken.getId().toString(), reason, adminId));

        UUID unknown = UUID.randomUUID();
        when(postRepository.findById(unknown)).thenReturn(Optional.empty());
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND,
                () -> service.takedownPost(unknown.toString(), reason, adminId));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                () -> service.takedownPost("not-a-uuid", reason, adminId));

        verify(postRepository, never()).save(any());
        verifyNoInteractions(auditLogService, notifier);
    }

    @Test
    void restorePost_clearsTheModerationAndAudits() {
        PostEntity post = post(author());
        post.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        post.setModerationReason("melanggar");
        post.setModeratedBy(adminId);
        post.setModeratedAt(Instant.now());
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPostDTO result = service.restorePost(post.getId().toString());

        assertEquals(ModerationStatus.ACTIVE, post.getModerationStatus());
        assertNull(post.getModerationReason());
        assertNull(post.getModeratedBy());
        assertNull(post.getModeratedAt());
        assertEquals(ModerationStatus.ACTIVE, result.moderationStatus());
        verify(auditLogService).record(eq(AdminAuditAction.POST_RESTORE), eq(AuditTargetType.POST),
                eq(post.getId().toString()), isNull(), any());
    }

    @Test
    void restorePost_rejectsNotTakenDownAndTheOnesTheOwnerDeletedMeanwhile() {
        PostEntity active = post(author());
        when(postRepository.findById(active.getId())).thenReturn(Optional.of(active));
        assertFails(GlobalMessage.CONTENT_NOT_TAKEN_DOWN, HttpStatus.CONFLICT,
                () -> service.restorePost(active.getId().toString()));

        PostEntity deletedAfterTakedown = post(author());
        deletedAfterTakedown.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        deletedAfterTakedown.setDeleted(true);
        when(postRepository.findById(deletedAfterTakedown.getId())).thenReturn(Optional.of(deletedAfterTakedown));
        assertFails(GlobalMessage.POST_ALREADY_DELETED, HttpStatus.CONFLICT,
                () -> service.restorePost(deletedAfterTakedown.getId().toString()));

        verify(postRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    // ------------------------------------------------------- post hard delete

    @Test
    @SuppressWarnings("unchecked")
    void hardDeletePost_removesLikesCommentsAndRowThenAuditsASnapshotThenDeletesTheMedia() {
        PostEntity post = post(author());
        post.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        post.setModerationReason("melanggar");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(likeRepository.deleteAllByPostId(post.getId())).thenReturn(3);
        when(commentRepository.deleteAllByPostId(post.getId())).thenReturn(2);
        when(reportClosingService.closeOpenReports(ReportTargetType.POST, post.getId(), adminId, ReportAction.HARD_DELETE)).thenReturn(2);
        when(reportClosingService.closeOpenReportsOfCommentsOnPost(post.getId(), adminId, ReportAction.HARD_DELETE)).thenReturn(1);

        Operation result = service.hardDeletePost(post.getId().toString(), reason, adminId);

        assertEquals(Operation.DELETED, result);
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        InOrder order = inOrder(reportClosingService, likeRepository, commentRepository, postRepository, auditLogService, fileStorageService);
        // the reports are closed before the comments they may point at are deleted
        order.verify(reportClosingService).closeOpenReportsOfCommentsOnPost(post.getId(), adminId, ReportAction.HARD_DELETE);
        order.verify(likeRepository).deleteAllByPostId(post.getId());
        order.verify(commentRepository).deleteAllByPostId(post.getId());
        order.verify(postRepository).delete(post);
        order.verify(auditLogService).record(eq(AdminAuditAction.POST_HARD_DELETE), eq(AuditTargetType.POST),
                eq(post.getId().toString()), eq("melanggar aturan"), metadata.capture());
        order.verify(fileStorageService).deleteObject("media.jpg");
        order.verify(fileStorageService).deleteObject("thumb.jpg");

        Map<String, Object> snapshot = metadata.getValue();
        assertEquals("author", snapshot.get("authorUsername"));
        assertEquals("a caption", snapshot.get("caption"));
        assertEquals("media.jpg", snapshot.get("mediaKey"));
        assertEquals("thumb.jpg", snapshot.get("thumbnailKey"));
        assertEquals(3, snapshot.get("likesRemoved"));
        assertEquals(2, snapshot.get("commentsRemoved"));
        assertEquals(3, snapshot.get("reportsClosed"));
        assertEquals("TAKEN_DOWN", snapshot.get("moderationStatus"));
        assertEquals(false, snapshot.get("deletedByOwner"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hardDeletePost_worksOnAPostTheOwnerAlreadyDeletedAndSkipsAMissingThumbnail() {
        PostEntity post = post(author());
        post.setDeleted(true);
        post.setThumbnailUrl(null);
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        service.hardDeletePost(post.getId().toString(), reason, adminId);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.POST_HARD_DELETE), eq(AuditTargetType.POST),
                any(), any(), metadata.capture());
        assertEquals(true, metadata.getValue().get("deletedByOwner"));
        verify(fileStorageService).deleteObject("media.jpg");
        verify(fileStorageService, never()).deleteObject("thumb.jpg");
    }

    @Test
    void hardDeletePost_unknownPostChangesNothing() {
        UUID unknown = UUID.randomUUID();
        when(postRepository.findById(unknown)).thenReturn(Optional.empty());

        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.hardDeletePost(unknown.toString(), reason, adminId));

        verifyNoInteractions(likeRepository, auditLogService, fileStorageService);
        verify(commentRepository, never()).deleteAllByPostId(any());
    }

    // ----------------------------------------------------------- admin detail

    @Test
    void getPost_includesRawCountsAndNoMediaUrlsForAPostTheOwnerDeleted() throws Exception {
        PostEntity post = post(author());
        post.setDeleted(true);
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(likeRepository.countByPostId(post.getId())).thenReturn(4L);
        when(commentRepository.countByPostId(post.getId())).thenReturn(6L);

        AdminPostDTO result = service.getPost(post.getId().toString());

        assertEquals(new AdminPostDTO.Stats(4, 6), result.stats());
        assertNull(result.mediaUrl());
        assertNull(result.thumbnailUrl());
        verify(fileStorageService, never()).getPresignUrl(any());
    }

    @Test
    void getPost_aFailedPresignDoesNotBreakTheDetail() throws Exception {
        PostEntity post = post(author());
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(fileStorageService.getPresignUrl(any())).thenThrow(new RuntimeException("storage down"));

        AdminPostDTO result = service.getPost(post.getId().toString());

        assertNull(result.mediaUrl());
        assertEquals("a caption", result.caption());
    }

    // ------------------------------------------------------------- comments

    @Test
    void takedownComment_hidesItAuditsAndNotifiesTheAuthor() {
        UserEntity author = author();
        CommentEntity comment = comment(author, post(author));
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCommentDTO result = service.takedownComment(comment.getId().toString(), reason, adminId);

        assertEquals(ModerationStatus.TAKEN_DOWN, comment.getModerationStatus());
        assertEquals("melanggar aturan", comment.getModerationReason());
        assertEquals(adminId, comment.getModeratedBy());
        assertEquals("rude", result.content());
        verify(auditLogService).record(eq(AdminAuditAction.COMMENT_TAKEDOWN), eq(AuditTargetType.COMMENT),
                eq(comment.getId().toString()), eq("melanggar aturan"), any());
        verify(notifier).commentTakenDown(author.getId(), "melanggar aturan");
        verify(reportClosingService).closeOpenReports(ReportTargetType.COMMENT, comment.getId(), adminId, ReportAction.TAKEDOWN);
    }

    @Test
    void takedownAndRestoreComment_rejectTheWrongState() {
        UserEntity author = author();
        CommentEntity taken = comment(author, post(author));
        taken.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        when(commentRepository.findById(taken.getId())).thenReturn(Optional.of(taken));
        assertFails(GlobalMessage.CONTENT_ALREADY_TAKEN_DOWN, HttpStatus.CONFLICT,
                () -> service.takedownComment(taken.getId().toString(), reason, adminId));

        CommentEntity active = comment(author, post(author));
        when(commentRepository.findById(active.getId())).thenReturn(Optional.of(active));
        assertFails(GlobalMessage.CONTENT_NOT_TAKEN_DOWN, HttpStatus.CONFLICT,
                () -> service.restoreComment(active.getId().toString()));

        verify(commentRepository, never()).save(any());
        verifyNoInteractions(auditLogService, notifier);
    }

    @Test
    void restoreComment_clearsTheModerationAndAudits() {
        UserEntity author = author();
        CommentEntity comment = comment(author, post(author));
        comment.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        comment.setModerationReason("kasar");
        comment.setModeratedBy(adminId);
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.restoreComment(comment.getId().toString());

        assertEquals(ModerationStatus.ACTIVE, comment.getModerationStatus());
        assertNull(comment.getModerationReason());
        assertNull(comment.getModeratedBy());
        verify(auditLogService).record(eq(AdminAuditAction.COMMENT_RESTORE), eq(AuditTargetType.COMMENT),
                eq(comment.getId().toString()), isNull(), any());
    }

    // --------------------------------------------------------------- filters

    @Test
    void search_rejectsInvalidFilters() {
        PageRequest page = PageRequest.of(0, 20);

        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchPosts(
                new AdminPostSearchRequest("not-a-uuid", null, null, null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchPosts(
                new AdminPostSearchRequest(null, "AUDIO", null, null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchPosts(
                new AdminPostSearchRequest(null, null, "HIDDEN", null, null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchPosts(
                new AdminPostSearchRequest(null, null, null, null, null, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7)), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchComments(
                new AdminCommentSearchRequest("not-a-uuid", null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> service.searchComments(
                new AdminCommentSearchRequest(null, null, "HIDDEN", null), page));

        verifyNoInteractions(postRepository, commentRepository);
    }
}
