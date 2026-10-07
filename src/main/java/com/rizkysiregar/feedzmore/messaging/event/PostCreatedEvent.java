package com.rizkysiregar.feedzmore.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreatedEvent {
    private UUID postId;
    private UUID authorId;
    private String username;
    private String caption;
    private String imageUrl;
    private LocalDateTime createdAt;
    private String email;
    private Map<String, Object> emailProps;
}
