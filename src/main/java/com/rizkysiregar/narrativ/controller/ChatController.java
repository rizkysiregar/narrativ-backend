package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.ChatTokenResponse;
import com.rizkysiregar.narrativ.model.response.DirectMessageResponse;
import com.rizkysiregar.narrativ.service.FeatureFlagService;
import com.rizkysiregar.narrativ.service.chat.ChatDirectMessageService;
import com.rizkysiregar.narrativ.service.chat.ChatTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
@Tag(name = "Chat")
public class ChatController {

    private final ChatTokenService chatTokenService;
    private final ChatDirectMessageService chatDirectMessageService;
    private final FeatureFlagService featureFlagService;

    @Operation(summary = "Token for the chat service (Marpokat)",
            description = "Syncs the user's chat profile and returns a Marpokat user token with the api key and address "
                    + "of this environment. Ask again shortly before expiresAt. 403 CHAT_DISABLED while an admin has chat switched off (feature.chat.enabled), 503 CHAT_UNAVAILABLE while chat is off or unreachable")
    @PostMapping("/token")
    @PreAuthorize("hasRole('USER')")
    public ChatTokenResponse token(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        featureFlagService.requireChatEnabled();
        return chatTokenService.issueToken(currentUser.getUserId());
    }

    @Operation(summary = "Open the direct message with another user",
            description = "Creates the chat channel of the two users when it does not exist yet and returns its id. "
                    + "A user on either side of a block does not exist for the other (404 USER_NOT_FOUND). "
                    + "400 for yourself, 403 CHAT_DISABLED while an admin has chat switched off (feature.chat.enabled), 503 CHAT_UNAVAILABLE while chat is off or unreachable")
    @PostMapping("/dm/{userId}")
    @PreAuthorize("hasRole('USER')")
    public DirectMessageResponse openDirectMessage(
            @PathVariable UUID userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        featureFlagService.requireChatEnabled();
        return chatDirectMessageService.open(currentUser.getUserId(), userId);
    }
}
