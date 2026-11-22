package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.response.ChatTokenResponse;
import com.rizkysiregar.narrativ.service.chat.ChatTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
@Tag(name = "Chat")
public class ChatController {

    private final ChatTokenService chatTokenService;

    @Operation(summary = "Token for the chat service (Marpokat)",
            description = "Syncs the user's chat profile and returns a Marpokat user token with the api key and address "
                    + "of this environment. Ask again shortly before expiresAt. 503 CHAT_UNAVAILABLE while chat is off or unreachable")
    @PostMapping("/token")
    @PreAuthorize("hasRole('USER')")
    public ChatTokenResponse token(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return chatTokenService.issueToken(currentUser.getUserId());
    }
}
