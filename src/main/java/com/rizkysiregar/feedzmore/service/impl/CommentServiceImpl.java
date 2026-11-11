package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.feedzmore.messaging.publisher.CommentEventPublisher;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.CommentService;
import com.rizkysiregar.feedzmore.util.AfterCommit;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostCrudRepository postCrudRepository;
    private final UserRepository userRepository;
    private final CommentEventPublisher commentEventPublisher;

    private static final int NOTIFICATION_PREVIEW_LENGTH = 100;

    @Override
    public Operation addComment(UUID postId, UUID userId, String content, UUID parentId) {
        // add new comment
        PostEntity post = postCrudRepository.findInteractableById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post Not Found"));

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
        if (!postCrudRepository.existsInteractableById(postId)) {
            throw new EntityNotFoundException("Post Not Found");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponse> commentPage = commentRepository.findTopLevelCommentResponses(postId, pageable);

        return new PaginatedResponse<>(
                commentPage.getContent().stream().map(comment -> comment.forViewer(viewerId)).toList(),
                commentPage.getNumber(),
                commentPage.getSize(),
                commentPage.getTotalElements(),
                commentPage.getTotalPages(),
                commentPage.isLast()
        );
    }

    @Override
    public List<CommentResponse> getReplies(UUID commentId, UUID viewerId) {
        return commentRepository.findReplyResponses(commentId).stream()
                .map(reply -> reply.forViewer(viewerId))
                .toList();
    }

    @Override
    public PaginatedResponse<CommentResponse> getRepliesPage(UUID commentId, int page, int size, UUID viewerId) {
        Page<CommentResponse> replyPage = commentRepository.findReplyResponsePage(commentId, PageRequest.of(page, size));
        return new PaginatedResponse<>(
                replyPage.getContent().stream().map(reply -> reply.forViewer(viewerId)).toList(),
                replyPage.getNumber(),
                replyPage.getSize(),
                replyPage.getTotalElements(),
                replyPage.getTotalPages(),
                replyPage.isLast()
        );
    }

    @Override
    public long getCountByPostId(UUID postId) {
        return commentRepository.contByPostId(postId);
    }

    private static String preview(String content) {
        if (content == null || content.length() <= NOTIFICATION_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "...";
    }
}
