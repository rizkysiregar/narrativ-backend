package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.ConversationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class ConversationResponse {
    UUID id;
    ConversationType type;
    String name;
    String avatarUrl;
    Instant updatedAt;
    long unreadCount;

}
