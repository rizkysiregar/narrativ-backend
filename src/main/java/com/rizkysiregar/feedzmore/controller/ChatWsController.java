package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.request.SendMessageRequest;
import com.rizkysiregar.feedzmore.service.chat.ChatService;
import com.rizkysiregar.feedzmore.service.chat.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Handles inbound STOMP frames from clients.
 * <p>
 * Client sends to /app/chat.send  → this controller processes it
 * then broadcasts to /topic/conversation.{id} → all subscribers receive it
 */
@Controller
@RequiredArgsConstructor
public class ChatWsController {
    private final ChatService chatService;
    private final PresenceService presenceService;

    /**
     * STOMP destination: /app/chat.send
     * Client sends a message. Server persists and broadcasts.
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload SendMessageRequest request,
                            Principal principal) {
        UUID userId = extractUserId(principal);
        chatService.sendMessage(userId, request);
    }

    /**
     * STOMP destination: /app/chat.read
     * Client acknowledges reading up to a message.
     */
    @MessageMapping("/chat.read")
    public void markRead(@Payload ReadAckPayload payload, Principal principal) {
        UUID userId = extractUserId(principal);
        chatService.markAsRead(userId, payload.conversationId(), payload.lastReadMessageId());
    }

    /**
     * STOMP destination: /app/chat.typing
     * Client sends typing start/stop event.
     * Server relays to other participants via Redis TTL (auto-expires).
     */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingPayload payload, Principal principal) {
        UUID userId = extractUserId(principal);
        presenceService.setTyping(payload.conversationId(), userId, payload.typing());
        // Broadcast to conversation subscribers
        // The SimpMessagingTemplate is used in PresenceService or here directly
    }

    /**
     * STOMP destination: /app/chat.heartbeat
     * Keeps the user's online presence alive in Redis.
     */
    @MessageMapping("/chat.heartbeat")
    public void heartbeat(Principal principal) {
        UUID userId = extractUserId(principal);
        presenceService.markOnline(userId);
    }

    private UUID extractUserId(Principal principal) {
        // Adapt to however your JWT sets the principal name
        // e.g., if your JWT puts userId as the subject:
        return UUID.fromString(principal.getName());
    }

    // Small payload records (can move to dto package)
    public record ReadAckPayload(UUID conversationId, UUID lastReadMessageId) {
    }

    public record TypingPayload(UUID conversationId, boolean typing) {
    }
}
