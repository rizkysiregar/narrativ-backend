package com.rizkysiregar.narrativ.model.response;

import com.rizkysiregar.narrativ.model.enums.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One inbox entry. Likes of the same post on the same day are one entry: [actors] holds the newest one or two
 * people and [actorCount] how many there are in all ("ani, budi and 3 others liked your post").
 *
 * @param groupKey       pass it back to mark this entry read
 * @param postThumbnailUrl the first photo of the post, or the poster of its first video; null for a text post
 * @param preview        the start of the comment, for comments, replies and pins
 */
public record NotificationResponse(
        String groupKey,
        NotificationType type,
        List<Actor> actors,
        long actorCount,
        UUID postId,
        String postThumbnailUrl,
        UUID commentId,
        String preview,
        Instant createdAt,
        boolean unread
) {
    public record Actor(UUID id, String username, String profileImageUrl) {
    }
}
