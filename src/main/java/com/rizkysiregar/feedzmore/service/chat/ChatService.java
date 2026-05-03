package com.rizkysiregar.feedzmore.service.chat;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.messaging.event.ReadReceiptEvent;
import com.rizkysiregar.feedzmore.model.document.MessageDocument;
import com.rizkysiregar.feedzmore.model.entity.chat.Message;
import com.rizkysiregar.feedzmore.model.entity.chat.MessageAttachment;
import com.rizkysiregar.feedzmore.model.enums.MessageStatus;
import com.rizkysiregar.feedzmore.model.enums.MessageType;
import com.rizkysiregar.feedzmore.model.request.SendMessageRequest;
import com.rizkysiregar.feedzmore.model.response.MessageResponse;
import com.rizkysiregar.feedzmore.repository.ConversationParticipantRepository;
import com.rizkysiregar.feedzmore.repository.MessageRepository;
import com.rizkysiregar.feedzmore.repository.elasticsearch.MessageSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageSearchRepository messageSearchRepository;
    private static final int PAGE_SIZE = 20;


    // -------------------------------
    // Send a message
    // -------------------------------
    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        // 1. Authorization: confirm the sender is a participant
        if (!participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(request.getConversationId(), senderId)) {
            log.warn("User {} is not a participant of conversation {}", senderId, request.getConversationId());
            throw new BusinessException(GlobalMessage.CHAT_NOT_A_PARTICIPANT, HttpStatus.FORBIDDEN);
        }

        // 2. Persist to PostgreSQL
        Message message = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .type(request.getType())
                .content(request.getContent())
                .status(MessageStatus.SENT)
                .replyToId(request.getReplyToId())
                .build();

        if (request.getAttachments() != null) {
            List<MessageAttachment> attachments = request.getAttachments().stream()
                    .map(a -> MessageAttachment.builder()
                            .message(message)
                            .url(a.getUrl())
                            .thumbnailUrl(a.getThumbnailUrl())
                            .fileName(a.getFileName())
                            .mimeType(a.getMimeType())
                            .fileSize(a.getFileSize())
                            .build())
                    .toList();
            message.setAttachments(attachments);
        }

        Message saved = messageRepository.save(message);
        MessageResponse response = toResponse(saved);

        // 3. Broadcast to all conversation subscribers via RabbitMQ relay
        messagingTemplate.convertAndSend(
                "/topic/conversation." + request.getConversationId(),
                response
        );

        // 4. Index in Elasticsearch asynchronously (non-blocking)
        indexMessageAsync(saved);
        return response;
    }

    // -------------------------------
    // Mark as read
    // -------------------------------
    @Transactional
    public void markAsRead(UUID userId, UUID conversationId, UUID lastReadMessageId) {
        participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .ifPresent(p -> {
                    p.setLastReadMessageId(lastReadMessageId);
                    participantRepository.save(p);
                });

        // Notify the sender(s) that their message has been read
        messagingTemplate.convertAndSend(
                "/topic/conversation." + conversationId + ".status",
                new ReadReceiptEvent(conversationId, userId, lastReadMessageId)
        );
    }

    // -------------------------------
    // Fetch message history (cursor-based pagination)
    // -------------------------------
    @Transactional(readOnly = true)
    public Slice<MessageResponse> getHistory(UUID conversationId, UUID beforeId) {
        Slice<Message> page = messageRepository.findByConversationIdBefore(
                conversationId, beforeId, PageRequest.of(0, PAGE_SIZE)
        );
        return page.map(this::toResponse);
    }


    @Async
    public void indexMessageAsync(Message message) {
        if (message.getContent() == null || message.getType() != MessageType.TEXT) return;
        try {
            messageSearchRepository.save(MessageDocument.builder()
                    .id(message.getId().toString())
                    .conversationId(message.getConversationId().toString())
                    .senderId(message.getSenderId().toString())
                    .content(message.getContent())
                    .createdAt(message.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.error("Error indexing message {}: {}", message.getId(), e.getMessage());
        }
    }

    private MessageResponse toResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .type(m.getType())
                .content(m.getContent())
                .status(m.getStatus())
                .replyToId(m.getReplyToId())
                .attachments(m.getAttachments().stream()
                        .map(a -> MessageResponse.AttachmentResponse.builder()
                                .id(a.getId())
                                .url(a.getUrl())
                                .thumbnailUrl(a.getThumbnailUrl())
                                .fileName(a.getFileName())
                                .mimeType(a.getMimeType())
                                .fileSize(a.getFileSize())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(m.getCreatedAt())
                .build();
    }

}
