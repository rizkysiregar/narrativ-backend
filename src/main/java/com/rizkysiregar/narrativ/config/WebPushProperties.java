package com.rizkysiregar.narrativ.config;

import com.rizkysiregar.narrativ.model.response.WebPushConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * The Firebase web app of this environment (APP_WEB_PUSH_*), handed to the web client so one web image works in
 * every environment, and the web client's address (APP_WEB_ORIGIN), which a web push opens when it is clicked.
 */
@Component
public class WebPushProperties {

    private final String origin;
    private final WebPushConfigResponse config;

    public WebPushProperties(@Value("${app.web.origin:}") String origin,
                             @Value("${app.web-push.api-key:}") String apiKey,
                             @Value("${app.web-push.project-id:}") String projectId,
                             @Value("${app.web-push.messaging-sender-id:}") String messagingSenderId,
                             @Value("${app.web-push.app-id:}") String appId,
                             @Value("${app.web-push.vapid-key:}") String vapidKey) {
        this.origin = origin.replaceAll("/+$", "");
        boolean complete = Stream.of(apiKey, projectId, messagingSenderId, appId, vapidKey).noneMatch(String::isBlank);
        this.config = complete
                ? new WebPushConfigResponse(true, apiKey, projectId, messagingSenderId, appId, vapidKey)
                : new WebPushConfigResponse(false, null, null, null, null, null);
    }

    /** The web client's address without a trailing slash, empty when not configured. */
    public String origin() {
        return origin;
    }

    public WebPushConfigResponse config() {
        return config;
    }
}
