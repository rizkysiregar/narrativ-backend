package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SendMessageRequest {
    @NotNull
    private UUID conversationId;
    @NotNull
    private MessageType type;
    private String content;           // required for TEXT
    private UUID replyToId;           // optional thread reply
    private List<AttachmentDto> attachments; // optional

    @Data
    public static class AttachmentDto {
        private String url;
        private String thumbnailUrl;
        private String fileName;
        private String mimeType;
        private Long fileSize;
    }
}