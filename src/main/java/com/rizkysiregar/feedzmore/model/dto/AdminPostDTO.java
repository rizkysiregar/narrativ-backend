package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record AdminPostDTO(
        String id,
        String authorId,
        String authorUsername,
        UserStatus authorStatus,
        String caption,
        List<Media> media,
        LocalDateTime createdAt,
        boolean deleted,
        ModerationStatus moderationStatus,
        String moderationReason,
        Instant moderatedAt,
        String moderatedBy,
        Stats stats
) {
    /** One image or video of the post, in display order; {@code url} and {@code thumbnailUrl} are presigned. */
    public record Media(String id, MediaType type, String url, String thumbnailUrl) {}

    /** Raw row counts for the admin detail view; null in list responses. */
    public record Stats(long likeCount, long commentCount) {}

    public static AdminPostDTO from(PostEntity post, List<Media> media, Stats stats) {
        return new AdminPostDTO(
                post.getId().toString(),
                post.getUser().getId().toString(),
                post.getUser().getUsername(),
                post.getUser().getStatus(),
                post.getCaption(),
                media,
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
