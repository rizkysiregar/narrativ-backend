package com.rizkysiregar.narrativ.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.stream.Stream;

/**
 * Connection to Marpokat, the chat service (repo {@code marpokat}). Narrativ is a tenant of it: the api key is public
 * (handed to the apps), the api secret signs the server and user tokens and never leaves this backend.
 * Chat is off while any of the three values is missing.
 */
@Component
public class MarpokatProperties {

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final Duration userTokenTtl;

    public MarpokatProperties(@Value("${app.marpokat.base-url:}") String baseUrl,
                              @Value("${app.marpokat.api-key:}") String apiKey,
                              @Value("${app.marpokat.api-secret:}") String apiSecret,
                              @Value("${app.marpokat.user-token-ttl:PT1H}") Duration userTokenTtl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.userTokenTtl = userTokenTtl;
    }

    public boolean enabled() {
        return Stream.of(baseUrl, apiKey, apiSecret).noneMatch(String::isEmpty);
    }

    /** e.g. https://api-dev-marpokat.rizkysiregar.my.id, without a trailing slash. */
    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String apiSecret() {
        return apiSecret;
    }

    public Duration userTokenTtl() {
        return userTokenTtl;
    }
}
