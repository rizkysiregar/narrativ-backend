package com.rizkysiregar.feedzmore.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommentResponse {
    private UUID id;
    @JsonIgnore
    private UUID authorId;
    private String username;
    private String profileImageUrl;
    private String commentContent;
    private LocalDateTime createdAt;
    // null for a top-level comment
    private UUID parentId;
    // the comment this one answers and its author, so the client can render the @username tag
    private UUID replyToCommentId;
    private String replyToUsername;
    // visible replies under a top-level comment, always 0 for a reply
    private long replyCount;
    // TAKEN_DOWN comments are still listed so the client can filter on this; the text is only sent to the author
    private ModerationStatus moderationStatus;
    private String takedownReason;
    private Instant takenDownAt;

    public CommentResponse(UUID id, UUID authorId, String username, String profileImageUrl, String commentContent,
                           ModerationStatus moderationStatus, String moderationReason, Instant moderatedAt,
                           UUID parentId, UUID replyToCommentId, String replyToUsername,
                           LocalDateTime createdAt, Long replyCount) {
        this.id = id;
        this.authorId = authorId;
        this.username = username;
        this.profileImageUrl = profileImageUrl;
        this.commentContent = commentContent;
        this.moderationStatus = moderationStatus;
        this.takedownReason = moderationReason;
        this.takenDownAt = moderatedAt;
        this.parentId = parentId;
        this.replyToCommentId = replyToCommentId;
        this.replyToUsername = replyToUsername;
        this.createdAt = createdAt;
        this.replyCount = replyCount == null ? 0 : replyCount;
    }

    /**
     * Applies the rule for taken down comments: the author still sees the text and the reason,
     * everybody else only sees that the comment is taken down.
     */
    public CommentResponse forViewer(UUID viewerId) {
        if (moderationStatus != ModerationStatus.TAKEN_DOWN) {
            takedownReason = null;
            takenDownAt = null;
            return this;
        }
        if (authorId == null || !authorId.equals(viewerId)) {
            commentContent = null;
            takedownReason = null;
        }
        return this;
    }
}
