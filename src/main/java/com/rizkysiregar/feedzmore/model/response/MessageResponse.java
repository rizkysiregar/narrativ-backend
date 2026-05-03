package com.rizkysiregar.feedzmore.model.response;

import com.rizkysiregar.feedzmore.model.enums.MessageStatus;
import com.rizkysiregar.feedzmore.model.enums.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {
    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private MessageType type;
    private String content;
    private MessageStatus status;
    private UUID replyToId;
    private List<AttachmentResponse> attachments;
    private Instant createdAt;

    @Data
    @Builder
    public static class AttachmentResponse {
        private UUID id;
        private String url;
        private String thumbnailUrl;
        private String fileName;
        private String mimeType;
        private Long fileSize;
    }
}