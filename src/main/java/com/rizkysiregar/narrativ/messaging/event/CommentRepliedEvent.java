package com.rizkysiregar.narrativ.messaging.event;

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
/**
 * A comment was added to a post: a top-level comment or a reply. Despite the name (kept so messages already in the
 * queue still deserialize) it is published for every comment; the consumer works out who must be told.
 */
public class CommentRepliedEvent {
    private UUID commentId;
    private UUID postId;
    private UUID replierId;
    private String replierUsername;
    // owner of the comment that was answered; null for a top-level comment or a reply to yourself
    private UUID recipientId;
    // owner of the post the comment is on; null on events published before it was added
    private UUID postOwnerId;
    private String contentPreview;
    private LocalDateTime createdAt;
}
