package com.rizkysiregar.feedzmore.messaging.event;

import java.util.UUID;

public record ReadReceiptEvent(UUID conversationId, UUID userId, UUID lastReadMessageId) {
}
