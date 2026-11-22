package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.SecretKey;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server API of Marpokat (spec: marpokat/spec/openapi.yaml, /v1/server/*) and the tokens Narrativ signs for it.
 * Every call carries the public api key plus a server token signed with the api secret, valid 60 seconds.
 */
@Component
public class MarpokatClient {

    static final String HEADER_API_KEY = "X-Api-Key";
    private static final Duration SERVER_TOKEN_TTL = Duration.ofSeconds(60);

    private final MarpokatProperties properties;
    private final Clock clock;
    private final RestClient http;

    public MarpokatClient(MarpokatProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HEADER_API_KEY, properties.apiKey())
                .build();
    }

    /** What PUT /v1/server/users/{external_id} accepts. avatarUrl must be a URL that does not expire. */
    public record UserProfile(String name, String avatarUrl, Map<String, Object> metadata) {
    }

    /** Creates or updates the user. Marpokat refuses tokens of users it does not know, so this runs first. */
    public void upsertUser(String externalId, UserProfile profile) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", profile.name());
        body.put("avatar_url", profile.avatarUrl());
        body.put("metadata", profile.metadata());
        try {
            http.put()
                    .uri("/v1/server/users/{id}", externalId)
                    .header("Authorization", "Bearer " + serverToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new MarpokatException("upsert user " + externalId + " answered " + res.getStatusCode()
                                + ": " + new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new MarpokatException("upsert user " + externalId + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets or creates the direct message of two users (both must exist in Marpokat) and returns the channel id.
     * Marpokat keeps one DM per pair, whichever order the ids come in.
     */
    public String getOrCreateDirectMessage(String userA, String userB) {
        try {
            Map<?, ?> channel = http.post()
                    .uri("/v1/server/channels/dm")
                    .header("Authorization", "Bearer " + serverToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("user_a", userA, "user_b", userB))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new MarpokatException("create dm answered " + res.getStatusCode()
                                + ": " + new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    })
                    .body(Map.class);
            Object id = channel == null ? null : channel.get("id");
            if (!(id instanceof String channelId) || channelId.isBlank()) {
                throw new MarpokatException("create dm answered without a channel id");
            }
            return channelId;
        } catch (RestClientException e) {
            throw new MarpokatException("create dm failed: " + e.getMessage(), e);
        }
    }

    /** A user token for the apps: sub is the Narrativ user id. */
    public SignedToken userToken(String externalId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.userTokenTtl());
        String token = Jwts.builder()
                .subject(externalId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
        return new SignedToken(token, expiresAt);
    }

    public record SignedToken(String token, Instant expiresAt) {
    }

    String serverToken() {
        Instant now = clock.instant();
        return Jwts.builder()
                .claim("server", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(SERVER_TOKEN_TTL)))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey key() {
        // Marpokat uses the secret string as is (UTF-8) as the HMAC key; "mps_" + 43 characters is over 256 bits
        return Keys.hmacShaKeyFor(properties.apiSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Marpokat could not be reached or refused the call. */
    public static class MarpokatException extends RuntimeException {
        public MarpokatException(String message) {
            super(message);
        }

        public MarpokatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
