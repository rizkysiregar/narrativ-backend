package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.AdminCommentDTO;
import com.rizkysiregar.narrativ.model.dto.AdminPostDTO;
import com.rizkysiregar.narrativ.model.entity.CommentEntity;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.enums.AdminAuditAction;
import com.rizkysiregar.narrativ.model.enums.AuditTargetType;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import com.rizkysiregar.narrativ.model.enums.ReportAction;
import com.rizkysiregar.narrativ.model.enums.ReportTargetType;
import com.rizkysiregar.narrativ.model.request.AdminCommentSearchRequest;
import com.rizkysiregar.narrativ.model.request.AdminPostSearchRequest;
import com.rizkysiregar.narrativ.model.request.ModerationReasonRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.CommentRepository;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.util.AdminDates;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.util.LikePatterns;
import com.rizkysiregar.narrativ.util.Operation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin moderation of posts and comments.
 * <p>
 * A takedown only hides the content (status + reason, media is kept) and can be undone. The one irreversible
 * action is {@link #hardDeletePost}; the owner's own delete (is_deleted) is a different thing and stays with the owner.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminContentModerationService {

    private static final int AUDIT_CAPTION_MAX = 500;

    private final PostCrudRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final PostMediaRepository postMediaRepository;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final ContentModerationNotifier notifier;
    private final ReportClosingService reportClosingService;

    // ------------------------------------------------------------------ posts

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminPostDTO> searchPosts(AdminPostSearchRequest request, Pageable pageable) {
        UUID authorId = parseOptionalUuid(request.authorId());
        MediaType mediaType = parseEnum(MediaType.class, request.mediaType());
        ModerationStatus moderationStatus = parseEnum(ModerationStatus.class, request.moderationStatus());
        requireValidRange(request.createdFrom(), request.createdTo());
        LocalDateTime createdFrom = AdminDates.startInclusive(request.createdFrom());
        LocalDateTime createdBefore = AdminDates.endExclusive(request.createdTo());
        String keyword = request.keyword() == null || request.keyword().isBlank() ? null : request.keyword();

        Page<PostEntity> page = postRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (authorId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), authorId));
            }
            if (mediaType != null) {
                predicates.add(hasMedia(root, query, cb, mediaType));
            }
            if (request.hasMedia() != null) {
                Predicate any = hasMedia(root, query, cb, null);
                predicates.add(request.hasMedia() ? any : cb.not(any));
            }
            if (moderationStatus != null) {
                predicates.add(cb.equal(root.get("moderationStatus"), moderationStatus));
            }
            if (request.deleted() != null) {
                predicates.add(request.deleted() ? cb.isTrue(root.get("isDeleted")) : cb.isFalse(root.get("isDeleted")));
            }
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(root.get("caption")), LikePatterns.contains(keyword), LikePatterns.ESCAPE));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdBefore != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdBefore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        Map<UUID, List<AdminPostDTO.Media>> media = mediaOf(page.getContent());
        return toPage(page, post -> toDto(post, media.getOrDefault(post.getId(), List.of()), null));
    }

    @Transactional(readOnly = true)
    public AdminPostDTO getPost(String postId) {
        PostEntity post = findPost(parseUuid(postId));
        AdminPostDTO.Stats stats = new AdminPostDTO.Stats(
                likeRepository.countByPostId(post.getId()),
                commentRepository.countByPostId(post.getId()));
        return toDto(post, mediaOf(List.of(post)).getOrDefault(post.getId(), List.of()), stats);
    }

    @Transactional
    public AdminPostDTO takedownPost(String postId, ModerationReasonRequest request, UUID adminId) {
        PostEntity post = findPost(parseUuid(postId));
        if (post.isDeleted()) {
            throw new BusinessException(GlobalMessage.POST_ALREADY_DELETED, HttpStatus.CONFLICT);
        }
        if (post.getModerationStatus() == ModerationStatus.TAKEN_DOWN) {
            throw new BusinessException(GlobalMessage.CONTENT_ALREADY_TAKEN_DOWN, HttpStatus.CONFLICT);
        }

        String reason = request.reason().trim();
        post.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        post.setModeratedAt(Instant.now());
        post.setModeratedBy(adminId);
        post.setModerationReason(reason);
        PostEntity saved = postRepository.save(post);

        reportClosingService.closeOpenReports(ReportTargetType.POST, saved.getId(), adminId, ReportAction.TAKEDOWN);
        auditLogService.record(AdminAuditAction.POST_TAKEDOWN, AuditTargetType.POST, saved.getId().toString(),
                reason, AuditLogService.metadata("authorId", saved.getUser().getId().toString()));
        notifier.postTakenDown(saved.getUser().getId(), reason);

        return toDto(saved, mediaOf(List.of(saved)).getOrDefault(saved.getId(), List.of()), null);
    }

    @Transactional
    public AdminPostDTO restorePost(String postId) {
        PostEntity post = findPost(parseUuid(postId));
        if (post.isDeleted()) {
            // the owner deleted it after the takedown: the media is gone, there is nothing left to restore
            throw new BusinessException(GlobalMessage.POST_ALREADY_DELETED, HttpStatus.CONFLICT);
        }
        if (post.getModerationStatus() != ModerationStatus.TAKEN_DOWN) {
            throw new BusinessException(GlobalMessage.CONTENT_NOT_TAKEN_DOWN, HttpStatus.CONFLICT);
        }

        clearModeration(post);
        PostEntity saved = postRepository.save(post);

        auditLogService.record(AdminAuditAction.POST_RESTORE, AuditTargetType.POST, saved.getId().toString(),
                null, AuditLogService.metadata("authorId", saved.getUser().getId().toString()));

        return toDto(saved, mediaOf(List.of(saved)).getOrDefault(saved.getId(), List.of()), null);
    }

    /**
     * Permanently removes a post of any state: its likes, its comments, the row and the media in object storage.
     * What was removed is kept in the audit log, including the storage keys so a file that could not be deleted
     * can be cleaned up by hand.
     */
    @Transactional
    public Operation hardDeletePost(String postId, ModerationReasonRequest request, UUID adminId) {
        PostEntity post = findPost(parseUuid(postId));
        String reason = request.reason().trim();

        List<PostMediaEntity> media = postMediaRepository.findByPostIdOrderByPositionAsc(post.getId());
        List<String> mediaKeys = media.stream().map(PostMediaEntity::getMediaKey).toList();
        List<String> thumbnailKeys = media.stream().map(PostMediaEntity::getThumbnailKey).filter(Objects::nonNull).toList();
        String caption = post.getCaption() != null && post.getCaption().length() > AUDIT_CAPTION_MAX
                ? post.getCaption().substring(0, AUDIT_CAPTION_MAX) : post.getCaption();

        // close the reports first: the comments they may point at are about to disappear with the post
        int reportsClosed = reportClosingService.closeOpenReports(ReportTargetType.POST, post.getId(), adminId, ReportAction.HARD_DELETE)
                + reportClosingService.closeOpenReportsOfCommentsOnPost(post.getId(), adminId, ReportAction.HARD_DELETE);

        int likesRemoved = likeRepository.deleteAllByPostId(post.getId());
        int commentsRemoved = commentRepository.deleteAllByPostId(post.getId());
        postMediaRepository.deleteAllByPostId(post.getId());
        postRepository.delete(post);

        auditLogService.record(AdminAuditAction.POST_HARD_DELETE, AuditTargetType.POST, post.getId().toString(),
                reason, AuditLogService.metadata(
                        "authorId", post.getUser().getId().toString(),
                        "authorUsername", post.getUser().getUsername(),
                        "caption", caption,
                        "mediaTypes", media.stream().map(m -> m.getMediaType().name()).toList(),
                        "mediaKeys", mediaKeys,
                        "thumbnailKeys", thumbnailKeys,
                        "createdAt", post.getCreatedAt() == null ? null : post.getCreatedAt().toString(),
                        "deletedByOwner", post.isDeleted(),
                        "moderationStatus", post.getModerationStatus().name(),
                        "moderationReason", post.getModerationReason(),
                        "likesRemoved", likesRemoved,
                        "commentsRemoved", commentsRemoved,
                        "reportsClosed", reportsClosed));

        // only after the commit, so a rolled back delete never loses the media. deleteObject logs its own failure.
        AfterCommit.run("delete media of post " + post.getId(), () -> {
            mediaKeys.forEach(fileStorageService::deleteObject);
            thumbnailKeys.forEach(fileStorageService::deleteObject);
        });

        return Operation.DELETED;
    }

    // --------------------------------------------------------------- comments

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminCommentDTO> searchComments(AdminCommentSearchRequest request, Pageable pageable) {
        UUID postId = parseOptionalUuid(request.postId());
        UUID authorId = parseOptionalUuid(request.authorId());
        ModerationStatus moderationStatus = parseEnum(ModerationStatus.class, request.moderationStatus());
        String keyword = request.keyword() == null || request.keyword().isBlank() ? null : request.keyword();

        Page<CommentEntity> page = commentRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (postId != null) {
                predicates.add(cb.equal(root.get("post").get("id"), postId));
            }
            if (authorId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), authorId));
            }
            if (moderationStatus != null) {
                predicates.add(cb.equal(root.get("moderationStatus"), moderationStatus));
            }
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(root.get("content")), LikePatterns.contains(keyword), LikePatterns.ESCAPE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return toPage(page, AdminCommentDTO::from);
    }

    @Transactional
    public AdminCommentDTO takedownComment(String commentId, ModerationReasonRequest request, UUID adminId) {
        CommentEntity comment = findComment(parseUuid(commentId));
        if (comment.getModerationStatus() == ModerationStatus.TAKEN_DOWN) {
            throw new BusinessException(GlobalMessage.CONTENT_ALREADY_TAKEN_DOWN, HttpStatus.CONFLICT);
        }

        String reason = request.reason().trim();
        comment.setModerationStatus(ModerationStatus.TAKEN_DOWN);
        comment.setModeratedAt(Instant.now());
        comment.setModeratedBy(adminId);
        comment.setModerationReason(reason);
        CommentEntity saved = commentRepository.save(comment);

        reportClosingService.closeOpenReports(ReportTargetType.COMMENT, saved.getId(), adminId, ReportAction.TAKEDOWN);
        auditLogService.record(AdminAuditAction.COMMENT_TAKEDOWN, AuditTargetType.COMMENT, saved.getId().toString(),
                reason, AuditLogService.metadata(
                        "authorId", saved.getUser().getId().toString(),
                        "postId", saved.getPost().getId().toString()));
        notifier.commentTakenDown(saved.getUser().getId(), reason);

        return AdminCommentDTO.from(saved);
    }

    @Transactional
    public AdminCommentDTO restoreComment(String commentId) {
        CommentEntity comment = findComment(parseUuid(commentId));
        if (comment.getModerationStatus() != ModerationStatus.TAKEN_DOWN) {
            throw new BusinessException(GlobalMessage.CONTENT_NOT_TAKEN_DOWN, HttpStatus.CONFLICT);
        }

        clearModeration(comment);
        CommentEntity saved = commentRepository.save(comment);

        auditLogService.record(AdminAuditAction.COMMENT_RESTORE, AuditTargetType.COMMENT, saved.getId().toString(),
                null, AuditLogService.metadata(
                        "authorId", saved.getUser().getId().toString(),
                        "postId", saved.getPost().getId().toString()));

        return AdminCommentDTO.from(saved);
    }

    // ---------------------------------------------------------------- helpers

    private void clearModeration(PostEntity post) {
        post.setModerationStatus(ModerationStatus.ACTIVE);
        post.setModeratedAt(null);
        post.setModeratedBy(null);
        post.setModerationReason(null);
    }

    private void clearModeration(CommentEntity comment) {
        comment.setModerationStatus(ModerationStatus.ACTIVE);
        comment.setModeratedAt(null);
        comment.setModeratedBy(null);
        comment.setModerationReason(null);
    }

    private PostEntity findPost(UUID id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private CommentEntity findComment(UUID id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    // media of a post the owner deleted is already gone, and a failed presign must not break the whole list
    private AdminPostDTO toDto(PostEntity post, List<AdminPostDTO.Media> media, AdminPostDTO.Stats stats) {
        return AdminPostDTO.from(post, post.isDeleted() ? List.of() : media, stats);
    }

    /** Media of the given posts in display order, presigned, loaded with one query. */
    private Map<UUID, List<AdminPostDTO.Media>> mediaOf(List<PostEntity> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        return postMediaRepository
                .findByPostIdInOrderByPostIdAscPositionAsc(posts.stream().map(PostEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(PostMediaEntity::getPostId,
                        Collectors.mapping(m -> new AdminPostDTO.Media(m.getId().toString(), m.getMediaType(),
                                presign(m.getMediaKey()),
                                m.getThumbnailKey() == null ? null : presign(m.getThumbnailKey())),
                                Collectors.toList())));
    }

    /** True when the post has a media, of the given type when one is passed. */
    private static Predicate hasMedia(Root<PostEntity> post, CriteriaQuery<?> query, CriteriaBuilder cb, MediaType type) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<PostMediaEntity> media = sub.from(PostMediaEntity.class);
        Predicate samePost = cb.equal(media.get("postId"), post.get("id"));
        sub.select(cb.literal(1)).where(type == null ? samePost : cb.and(samePost, cb.equal(media.get("mediaType"), type)));
        return cb.exists(sub);
    }

    private String presign(String objectKey) {
        try {
            return fileStorageService.getPresignUrl(objectKey);
        } catch (Exception e) {
            log.warn("ADMIN.POST.PRESIGN.SKIPPED objectKey={} reason={}", objectKey, e.getMessage());
            return null;
        }
    }

    private <E, D> PaginatedResponse<D> toPage(Page<E> page, java.util.function.Function<E, D> mapper) {
        return new PaginatedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    private void requireValidRange(java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
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
