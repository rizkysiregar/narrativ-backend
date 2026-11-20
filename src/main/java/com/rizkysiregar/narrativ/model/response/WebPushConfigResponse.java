package com.rizkysiregar.narrativ.model.response;

/**
 * What the web client needs to ask Firebase for a push token in this environment: the Firebase web app's public
 * config and the VAPID key. None of it is secret. {@code enabled} is false while the environment has no web app.
 */
public record WebPushConfigResponse(
        boolean enabled,
        String apiKey,
        String projectId,
        String messagingSenderId,
        String appId,
        String vapidKey
) {
}
