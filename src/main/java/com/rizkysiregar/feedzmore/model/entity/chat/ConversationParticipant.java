package com.rizkysiregar.feedzmore.model.entity.chat;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private Instant joinedAt;
    private Instant leftAt;
    @Column(nullable = false)
    @Builder.Default
    private Boolean isAdmin = false;
    private UUID lastReadMessageId;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }
}