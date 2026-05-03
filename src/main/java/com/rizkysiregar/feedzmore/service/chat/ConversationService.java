package com.rizkysiregar.feedzmore.service.chat;

import com.rizkysiregar.feedzmore.model.entity.chat.Conversation;
import com.rizkysiregar.feedzmore.model.entity.chat.ConversationParticipant;
import com.rizkysiregar.feedzmore.model.enums.ConversationType;
import com.rizkysiregar.feedzmore.model.request.CreateConversationRequest;
import com.rizkysiregar.feedzmore.model.response.ConversationResponse;
import com.rizkysiregar.feedzmore.repository.ConversationParticipantRepository;
import com.rizkysiregar.feedzmore.repository.ConversationRepository;
import com.rizkysiregar.feedzmore.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(UUID userId) {
        return conversationRepository.findActiveByUserId(userId)
                .stream()
                .map(c -> toResponse(c, userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationResponse createConversation(UUID creatorId,
                                                   CreateConversationRequest request) {
        boolean isDirect = request.getParticipantIds().size() == 1;
        // For direct chats: return existing conversation if one already exists
        if (isDirect) {
            UUID otherId = request.getParticipantIds().get(0);
            return conversationRepository
                    .findDirectConversation(creatorId, otherId)
                    .map(c -> toResponse(c, creatorId))
                    .orElseGet(() -> createNew(creatorId, request, ConversationType.DIRECT));
        }
        return createNew(creatorId, request, ConversationType.GROUP);
    }

    private ConversationResponse createNew(UUID creatorId,
                                           CreateConversationRequest request,
                                           ConversationType type) {
        Conversation conversation = Conversation.builder()
                .type(type)
                .name(request.getName())
                .createdBy(creatorId)
                .build();
        // Add creator as admin participant
        List<ConversationParticipant> participants = new ArrayList<>();
        participants.add(ConversationParticipant.builder()
                .conversation(conversation)
                .userId(creatorId)
                .isAdmin(true)
                .build());
        // Add other participants
        request.getParticipantIds().forEach(uid ->
                participants.add(ConversationParticipant.builder()
                        .conversation(conversation)
                        .userId(uid)
                        .isAdmin(false)
                        .build())
        );
        conversation.setParticipants(participants);
        Conversation saved = conversationRepository.save(conversation);
        return toResponse(saved, creatorId);
    }

    private ConversationResponse toResponse(Conversation c, UUID currentUserId) {
        long unreadCount = participantRepository
                .findByConversationIdAndUserId(c.getId(), currentUserId)
                .map(p -> messageRepository.countUnread(c.getId(), currentUserId, p.getLastReadMessageId()))
                .orElse(0L);
        return ConversationResponse.builder()
                .id(c.getId())
                .type(c.getType())
                .name(c.getName())
                .avatarUrl(c.getAvatarUrl())
                .updatedAt(c.getUpdatedAt())
                .unreadCount(unreadCount)
                .build();
    }
}
