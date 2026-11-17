package com.rizkysiregar.narrativ.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Somebody liked a post for the first time (an already existing like publishes nothing). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLikedEvent {
    private UUID postId;
    private UUID postOwnerId;
    private UUID likerId;
    private String likerUsername;
    private LocalDateTime createdAt;
}
