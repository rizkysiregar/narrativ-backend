package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record AdminPostDTO(
        String id,
        String authorId,
        String authorUsername,
        UserStatus authorStatus,
        String caption,
        MediaType mediaType,
        String mediaUrl,
        String thumbnailUrl,
        LocalDateTime createdAt,
        boolean deleted,
        ModerationStatus moderationStatus,
        String moderationReason,
        Instant moderatedAt,
        String moderatedBy,
        Stats stats
) {
    /** Raw row counts for the admin detail view; null in list responses. */
    public record Stats(long likeCount, long commentCount) {}

    public static AdminPostDTO from(PostEntity post, String mediaUrl, String thumbnailUrl, Stats stats) {
        return new AdminPostDTO(
                post.getId().toString(),
                post.getUser().getId().toString(),
                post.getUser().getUsername(),
                post.getUser().getStatus(),
                post.getCaption(),
                post.getMediaType(),
                mediaUrl,
                thumbnailUrl,
                post.getCreatedAt(),
                post.isDeleted(),
                post.getModerationStatus(),
                post.getModerationReason(),
                post.getModeratedAt(),
                post.getModeratedBy() == null ? null : post.getModeratedBy().toString(),
                stats
        );
    }
}
