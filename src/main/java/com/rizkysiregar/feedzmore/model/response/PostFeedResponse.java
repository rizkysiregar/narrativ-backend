package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Getter
@Setter
public class PostFeedResponse {
    private UUID id;
    private String username;
    private String caption;
    // empty for a caption-only post
    private List<PostMediaResponse> media = List.of();
    private LocalDateTime createdAt;
    private long likeCount;
    private long commentCount;
    private boolean isLiked;
    // TAKEN_DOWN posts are still listed so the client can filter on this; caption and media are only sent to the author
    private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;
    private String takedownReason;
    private Instant takenDownAt;

    public PostFeedResponse(UUID id, String username, String caption, List<PostMediaResponse> media,
                            LocalDateTime createdAt,
                            long likeCount, long commentCount, boolean isLiked) {
        this.id = id;
        this.username = username;
        this.caption = caption;
        this.media = media;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.isLiked = isLiked;
    }
}
