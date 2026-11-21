package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.response.GoogleSignInConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Google OAuth client id for the web client's Google Identity Services button, read at runtime so one web image
 * runs in every environment and the button always asks for tokens with the audience this backend verifies.
 */
@RestController
@Tag(name = "Authentication and User Related API's")
public class GoogleSignInConfigController {

    private final String clientId;

    public GoogleSignInConfigController(@Value("${google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    @Operation(summary = "Google OAuth client id of this environment, for the web client's Sign in with Google button",
            description = "Public value, no login needed. enabled=false while the environment has no client id")
    @GetMapping("auth/google-config")
    public GoogleSignInConfigResponse googleConfig() {
        return clientId.isEmpty()
                ? new GoogleSignInConfigResponse(false, null)
                : new GoogleSignInConfigResponse(true, clientId);
    }
}
