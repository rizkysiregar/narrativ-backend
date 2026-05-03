package com.rizkysiregar.feedzmore.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TypingIndicatorEvent {
    private UUID conversationId;
    private Long userId;
    private boolean typing;
}
