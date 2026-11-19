package com.rizkysiregar.narrativ.messaging.event;

import com.rizkysiregar.narrativ.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Something somebody did to another user that goes to their inbox and their devices, for the actions without an
 * event of their own: a follow ([NotificationType.FOLLOWED]) and a pinned comment ([NotificationType.COMMENT_PINNED]).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {
    private NotificationType type;
    private UUID recipientId;
    private UUID actorId;
    private String actorUsername;
    // the pinned comment and its post; null for a follow
    private UUID postId;
    private UUID commentId;
    private String preview;
}
