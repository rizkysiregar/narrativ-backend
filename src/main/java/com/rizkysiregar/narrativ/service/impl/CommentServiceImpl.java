package com.rizkysiregar.narrativ.service.impl;

import com.rizkysiregar.narrativ.model.response.CommentResponse;
import com.rizkysiregar.narrativ.model.entity.CommentEntity;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.narrativ.messaging.publisher.CommentEventPublisher;
import com.rizkysiregar.narrativ.repository.CommentRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.CommentService;
import com.rizkysiregar.narrativ.service.ProfileImageUrlResolver;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.util.Operation;
import com.rizkysiregar.narrativ.util.Paging;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserBlockRepository userBlockRepository;
    private final PostCrudRepository postCrudRepository;
    private final UserRepository userRepository;
    private final CommentEventPublisher commentEventPublisher;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    private static final int NOTIFICATION_PREVIEW_LENGTH = 100;

    @Override
    public Operation addComment(UUID postId, UUID userId, String rawContent, UUID parentId) {
        // the controller validates the request; this keeps a blank comment out for any other caller too
        if (rawContent == null || rawContent.isBlank()) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        String content = rawContent.trim();

        // add new comment
        PostEntity post = postCrudRepository.findInteractableById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post Not Found"));
        // a block hides the post for both sides, so it answers like a post that is gone
        if (userBlockRepository.existsBetween(userId, post.getUser().getId())) {
            throw new EntityNotFoundException("Post Not Found");
        }

        // replies are one level deep: parent is always the top-level comment, replyTo is the comment answered
        CommentEntity replyTo = null;
        CommentEntity parentComment = null;
        if (parentId != null) {
            replyTo = commentRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));
            if (replyTo.getModerationStatus() == ModerationStatus.TAKEN_DOWN
                    || !post.getId().equals(replyTo.getPost().getId())) {
                throw new EntityNotFoundException("Parent comment not found");
            }
            // no reply, and so no notification, to someone on either side of a block
            if (userBlockRepository.existsBetween(userId, replyTo.getUser().getId())) {
                throw new EntityNotFoundException("Parent comment not found");
            }
            parentComment = replyTo.getParent() != null ? replyTo.getParent() : replyTo;
        }

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        CommentEntity comment = CommentEntity.builder()
                .post(post)
                .user(user)
                .content(content)
                .parent(parentComment)
                .replyTo(replyTo)
                .build();

        commentRepository.save(comment);

        if (replyTo != null && !replyTo.getUser().getId().equals(userId)) {
            CommentRepliedEvent event = CommentRepliedEvent.builder()
                    .commentId(comment.getId())
                    .postId(post.getId())
                    .replierId(userId)
                    .replierUsername(user.getUsername())
                    .recipientId(replyTo.getUser().getId())
                    .contentPreview(preview(content))
                    .createdAt(comment.getCreatedAt())
                    .build();
            AfterCommit.run("comment reply event " + comment.getId(),
                    () -> commentEventPublisher.publishCommentReplied(event));
        }

        return Operation.CREATED;
    }

    @Override
    public PaginatedResponse<CommentResponse> getComment(UUID postId, int page, int size, UUID viewerId) {
        Pageable pageable = Paging.of(page, size);
        UUID postOwnerId = postCrudRepository.findInteractableById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post Not Found"))
                .getUser().getId();
        if (userBlockRepository.existsBetween(viewerId, postOwnerId)) {
            throw new EntityNotFoundException("Post Not Found");
        }
        Page<CommentResponse> commentPage = commentRepository.findTopLevelCommentResponses(postId, viewerId, pageable);

        return new PaginatedResponse<>(
                commentPage.getContent().stream().map(comment -> prepare(comment, viewerId, postOwnerId)).toList(),
                commentPage.getNumber(),
                commentPage.getSize(),
                commentPage.getTotalElements(),
                commentPage.getTotalPages(),
                commentPage.isLast()
        );
    }

    @Override
    public List<CommentResponse> getReplies(UUID commentId, UUID viewerId) {
        UUID postOwnerId = commentRepository.findPostOwnerId(commentId).orElse(null);
        return commentRepository.findReplyResponses(commentId, viewerId).stream()
                .map(reply -> prepare(reply, viewerId, postOwnerId))
                .toList();
    }

    @Override
    public PaginatedResponse<CommentResponse> getRepliesPage(UUID commentId, int page, int size, UUID viewerId) {
        Pageable pageable = Paging.of(page, size);
        UUID postOwnerId = commentRepository.findPostOwnerId(commentId).orElse(null);
        Page<CommentResponse> replyPage = commentRepository.findReplyResponsePage(commentId, viewerId, pageable);
        return new PaginatedResponse<>(
                replyPage.getContent().stream().map(reply -> prepare(reply, viewerId, postOwnerId)).toList(),
                replyPage.getNumber(),
                replyPage.getSize(),
                replyPage.getTotalElements(),
                replyPage.getTotalPages(),
                replyPage.isLast()
        );
    }

    /**
     * The author may delete their own comment and the owner of the post may delete any comment on it.
     * Deleting a top-level comment takes its replies along; deleting a reply only clears the @tag of
     * the replies that answered it.
     */
    @Override
    @Transactional
    public Operation deleteComment(UUID commentId, UUID requesterId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        boolean isAuthor = comment.getUser().getId().equals(requesterId);
        boolean isPostOwner = comment.getPost().getUser().getId().equals(requesterId);
        if (!isAuthor && !isPostOwner) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        if (comment.getParent() == null) {
            commentRepository.deleteAllByParentId(commentId);
        } else {
            commentRepository.clearReplyTo(commentId);
        }
        commentRepository.delete(comment);
        commentRepository.flush();
        log.info("COMMENT.DELETED commentId={} requesterId={} asPostOwner={}", commentId, requesterId, !isAuthor);
        return Operation.DELETED;
    }

    @Override
    public long getCountByPostId(UUID postId, UUID viewerId) {
        return commentRepository.contByPostId(postId, viewerId);
    }

    private CommentResponse prepare(CommentResponse comment, UUID viewerId, UUID postOwnerId) {
        return withResolvedImage(comment.forViewer(viewerId).withDeletePermission(viewerId, postOwnerId));
    }

    private CommentResponse withResolvedImage(CommentResponse comment) {
        comment.setProfileImageUrl(profileImageUrlResolver.resolve(comment.getProfileImageUrl()));
        return comment;
    }

    private static String preview(String content) {
        if (content == null || content.length() <= NOTIFICATION_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "...";
    }
}
