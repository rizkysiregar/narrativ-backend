package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.MediaType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
public class PostFeedResponse {
    private UUID id;
    private String username;
    private String caption;
    private String mediaUrl;
    private MediaType mediaType;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private long likeCount;
    private long commentCount;
    private boolean isLiked;

    public PostFeedResponse(UUID id, String username, String caption, String mediaUrl, MediaType mediaType,
                            String thumbnailUrl, LocalDateTime createdAt,
                            long likeCount, long commentCount, boolean isLiked) {
        this.id = id;
        this.username = username;
        this.caption = caption;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.isLiked = isLiked;
    }
}
