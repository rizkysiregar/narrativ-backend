package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateConversationRequest {
    // For GROUP: provide a name. For DIRECT: exactly one participantId.
    private String name;
    @NotEmpty
    private List<UUID> participantIds; // does NOT include the requester; server adds them
}