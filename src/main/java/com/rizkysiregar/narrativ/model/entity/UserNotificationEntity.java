package com.rizkysiregar.narrativ.model.entity;

import com.rizkysiregar.narrativ.model.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One thing that happened to [recipientId]: written by NotificationInboxService, read by the inbox endpoints. */
@Entity
@Table(name = "user_notification")
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private UserEntity actor;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(length = 200)
    private String preview;

    @Column(name = "dedup_key", nullable = false, length = 160)
    private String dedupKey;

    @Column(name = "group_key", nullable = false, length = 160)
    private String groupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
