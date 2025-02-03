package com.rizkysiregar.feedzmore.model.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
public class PostDTO {
    private UUID id;
    private String username;
    private String caption;
    private String imageUrl;
    private LocalDateTime createdAt;
    private long likeCount;  // Use long instead of String
    private long commentCount; // Use long instead of String
    private boolean isLiked;

    public PostDTO(UUID id, String username, String caption, String imageUrl, LocalDateTime createdAt,
                   long likeCount, long commentCount, boolean isLiked) {
        this.id = id;
        this.username = username;
        this.caption = caption;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.isLiked = isLiked;
    }
}

