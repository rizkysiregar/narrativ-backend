package com.rizkysiregar.narrativ.model.response;

import java.time.Instant;

/**
 * What an app needs to connect to the chat service: the Marpokat user token (ask again before {@code expiresAt}), the
 * public api key, the Marpokat address of this environment, and the chat user id (the Narrativ user id).
 */
public record ChatTokenResponse(String token, Instant expiresAt, String apiKey, String baseUrl, String userId) {
}
