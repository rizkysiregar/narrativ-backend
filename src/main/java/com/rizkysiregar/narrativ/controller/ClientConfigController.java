package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.response.ClientConfigResponse;
import com.rizkysiregar.narrativ.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The feature switches of the apps, flipped by an admin in App management ({@code feature.*} parameters), so a
 * feature that is not ready can be hidden without a release.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Client config")
public class ClientConfigController {

    private final FeatureFlagService featureFlagService;

    @Operation(summary = "Which features the web and Android apps show",
            description = "Public, no login needed. Ask at start and when the app comes back to the foreground. "
                    + "features.chat=false: hide the messages menu, the message button and chat notifications")
    @GetMapping("config/client")
    public ClientConfigResponse clientConfig() {
        return new ClientConfigResponse(new ClientConfigResponse.Features(featureFlagService.isChatEnabled()));
    }
}
