package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.rizkysiregar.feedzmore.model.enums.ModerationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private CommentEntity parent;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // admin moderation, independent of the owner's own delete
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;
}
