package com.rizkysiregar.feedzmore.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRepliedEvent {
    private UUID commentId;
    private UUID postId;
    private UUID replierId;
    private String replierUsername;
    // owner of the comment that was answered
    private UUID recipientId;
    private String contentPreview;
    private LocalDateTime createdAt;
}
