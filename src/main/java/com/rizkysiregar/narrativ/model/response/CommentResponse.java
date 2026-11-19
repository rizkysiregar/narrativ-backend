package com.rizkysiregar.narrativ.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Data
public class CommentResponse {
    private UUID id;
    // lets the client report or block the author
    @JsonProperty("userId")
    private UUID authorId;
    private String username;
    private String profileImageUrl;
    private String commentContent;
    // an absolute moment (serialized with a Z) so clients in any time zone show the right time
    private Instant createdAt;
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
    // true for the author of the comment and for the owner of the post it is on
    private boolean canDelete;
    // the words of the caption this comment answers, null for a plain comment
    private String quoteText;
    // pinned on top of the comments by the author of the post; listed first
    private boolean pinned;

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
        // the column is a zone-less local date time written in the JVM default zone
        this.createdAt = createdAt == null ? null : createdAt.atZone(ZoneId.systemDefault()).toInstant();
        this.replyCount = replyCount == null ? 0 : replyCount;
    }

    public CommentResponse(UUID id, UUID authorId, String username, String profileImageUrl, String commentContent,
                           ModerationStatus moderationStatus, String moderationReason, Instant moderatedAt,
                           UUID parentId, UUID replyToCommentId, String replyToUsername,
                           LocalDateTime createdAt, Long replyCount, String quoteText, Boolean pinned) {
        this(id, authorId, username, profileImageUrl, commentContent, moderationStatus, moderationReason, moderatedAt,
                parentId, replyToCommentId, replyToUsername, createdAt, replyCount);
        this.quoteText = quoteText;
        this.pinned = Boolean.TRUE.equals(pinned);
    }

    public CommentResponse withDeletePermission(UUID viewerId, UUID postOwnerId) {
        canDelete = viewerId != null && (viewerId.equals(authorId) || viewerId.equals(postOwnerId));
        return this;
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
