package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.request.CreateConversationRequest;
import com.rizkysiregar.feedzmore.model.response.ConversationResponse;
import com.rizkysiregar.feedzmore.model.response.MessageResponse;
import com.rizkysiregar.feedzmore.service.chat.ChatService;
import com.rizkysiregar.feedzmore.service.chat.ConversationService;
import com.rizkysiregar.feedzmore.service.chat.MessageSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints used for:
 * - Fetching conversation list (on app open)
 * - Loading message history (paginated)
 * - Creating conversations
 * - Searching messages
 * <p>
 * Real-time events go through WebSocket. REST is for initial data loads.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRestController {
    private final ChatService chatService;
    private final ConversationService conversationService;
    private final MessageSearchService searchService;

    // GET /api/v1/chat/conversations
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(conversationService.getConversations(userId));
    }

    // POST /api/v1/chat/conversations
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok(conversationService.createConversation(userId, request));
    }

    // GET /api/v1/chat/conversations/{id}/messages?before={messageId}
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Slice<MessageResponse>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID before,
            @AuthenticationPrincipal Long userId) {
        // Authorization check is inside service
        return ResponseEntity.ok(chatService.getHistory(conversationId, before));
    }

    // GET /api/v1/chat/conversations/{id}/messages/search?q=hello
    @GetMapping("/conversations/{conversationId}/messages/search")
    public ResponseEntity<?> searchMessages(
            @PathVariable UUID conversationId,
            @RequestParam String q,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(searchService.search(conversationId, q, userId));
    }
}
