package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.CommentEntity;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record AdminCommentDTO(
        String id,
        String postId,
        String authorId,
        String authorUsername,
        String parentId,
        String content,
        LocalDateTime createdAt,
        ModerationStatus moderationStatus,
        String moderationReason,
        Instant moderatedAt,
        String moderatedBy
) {
    public static AdminCommentDTO from(CommentEntity comment) {
        return new AdminCommentDTO(
                comment.getId().toString(),
                comment.getPost().getId().toString(),
                comment.getUser().getId().toString(),
                comment.getUser().getUsername(),
                comment.getParent() == null ? null : comment.getParent().getId().toString(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getModerationStatus(),
                comment.getModerationReason(),
                comment.getModeratedAt(),
                comment.getModeratedBy() == null ? null : comment.getModeratedBy().toString()
        );
    }
}
