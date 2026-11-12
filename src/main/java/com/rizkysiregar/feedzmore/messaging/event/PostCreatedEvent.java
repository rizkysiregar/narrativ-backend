package com.rizkysiregar.feedzmore.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreatedEvent {
    private UUID postId;
    private UUID authorId;
    private String username;
    private String caption;
    private int mediaCount;
    private LocalDateTime createdAt;
    @ToString.Exclude // author email, the events are logged on publish and consume
    private String email;
    private Map<String, Object> emailProps;
}
