package com.rizkysiregar.feedzmore.service.chat;

import com.rizkysiregar.feedzmore.model.document.MessageDocument;
import com.rizkysiregar.feedzmore.repository.ConversationParticipantRepository;
import com.rizkysiregar.feedzmore.repository.elasticsearch.MessageSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageSearchService {
    private final MessageSearchRepository searchRepository;
    private final ConversationParticipantRepository participantRepository;

    public Page<MessageDocument> search(UUID conversationId, String query, UUID userId) {
        // Auth check: must be a participant
        if (!participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(
                conversationId, userId)) {
            throw new SecurityException("Not a participant");
        }
        return searchRepository.findByConversationIdAndContentContaining(
                conversationId.toString(),
                query,
                PageRequest.of(0, 20)
        );
    }
}
