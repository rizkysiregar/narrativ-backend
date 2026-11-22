package com.rizkysiregar.narrativ.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.narrativ.config.MarpokatProperties;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Talks to a local HTTP server standing in for Marpokat, so the wire format is checked, not just the calls. */
class MarpokatClientTest {

    private static final String SECRET = "mps_test-secret-test-secret-test-secret-abcdefg";

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"id\":\"x\"}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            query.set(exchange.getRequestURI().getRawQuery());
            apiKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (status == 200 || status == 204 ? responseBody : "{\"error\":{\"code\":\"unauthenticated\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : out.length);
            if (status != 204) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MarpokatClient client() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new MarpokatClient(new MarpokatProperties(base, "mpk_key", SECRET, Duration.ofHours(1)), Clock.systemUTC());
    }

    @Test
    void upsertUser_SendsTheServerApiRequest() throws Exception {
        client().upsertUser("0193a000-0000-7000-8000-000000000001",
                new MarpokatClient.UserProfile("Rizky", null, Map.of("username", "rizky")));

        assertThat(method.get()).isEqualTo("PUT");
        assertThat(path.get()).isEqualTo("/v1/server/users/0193a000-0000-7000-8000-000000000001");
        assertThat(apiKey.get()).isEqualTo("mpk_key");

        assertThat(authorization.get()).startsWith("Bearer ");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(authorization.get().substring(7)).getPayload();
        assertThat(claims.get("server", Boolean.class)).isTrue();
        long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(lifetime).isLessThanOrEqualTo(Duration.ofMinutes(5).toMillis()); // Marpokat's limit
        assertThat(claims.getExpiration()).isAfter(new Date());

        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("name").asText()).isEqualTo("Rizky");
        assertThat(json.get("avatar_url").isNull()).isTrue();
        assertThat(json.get("metadata").get("username").asText()).isEqualTo("rizky");
    }

    @Test
    void getOrCreateDirectMessage_PostsBothUsersAndReturnsTheChannelId() throws Exception {
        responseBody = "{\"id\":\"0193a000-aaaa-7000-8000-000000000009\",\"type\":\"dm\"}";

        String id = client().getOrCreateDirectMessage("user-a", "user-b");

        assertThat(id).isEqualTo("0193a000-aaaa-7000-8000-000000000009");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v1/server/channels/dm");
        assertThat(apiKey.get()).isEqualTo("mpk_key");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("user_a").asText()).isEqualTo("user-a");
        assertThat(json.get("user_b").asText()).isEqualTo("user-b");
        assertThat(authorization.get()).startsWith("Bearer ");
    }

    @Test
    void getOrCreateDirectMessage_AnswerWithoutId_Throws() {
        responseBody = "{\"type\":\"dm\"}";
        assertThatThrownBy(() -> client().getOrCreateDirectMessage("a", "b"))
                .isInstanceOf(MarpokatClient.MarpokatException.class);
    }

    @Test
    void getOrCreateDirectMessage_ErrorStatus_Throws() {
        status = 404;
        assertThatThrownBy(() -> client().getOrCreateDirectMessage("a", "b"))
                .isInstanceOf(MarpokatClient.MarpokatException.class)
                .hasMessageContaining("404");
    }

    @Test
    void upsertUser_ErrorStatus_Throws() {
        status = 401;
        assertThatThrownBy(() -> client().upsertUser("u_1", new MarpokatClient.UserProfile("x", null, Map.of())))
                .isInstanceOf(MarpokatClient.MarpokatException.class)
                .hasMessageContaining("401");
    }

    @Test
    void upsertUser_Unreachable_Throws() {
        MarpokatClient unreachable = new MarpokatClient(
                new MarpokatProperties("http://127.0.0.1:1", "mpk_key", SECRET, Duration.ofHours(1)), Clock.systemUTC());
        assertThatThrownBy(() -> unreachable.upsertUser("u_1", new MarpokatClient.UserProfile("x", null, Map.of())))
                .isInstanceOf(MarpokatClient.MarpokatException.class);
    }

    @Test
    void findDirectMessage_AsksWithoutCreatingAndReadsTheId() {
        responseBody = "{\"id\":\"chan-9\"}";

        assertThat(client().findDirectMessage("u1", "u2")).contains("chan-9");

        assertThat(method.get()).isEqualTo("GET");
        assertThat(path.get()).isEqualTo("/v1/server/channels/dm");
        assertThat(query.get()).isEqualTo("user_a=u1&user_b=u2");
    }

    @Test
    void findDirectMessage_NotFoundIsEmptyNotAnError() {
        status = 404;

        assertThat(client().findDirectMessage("u1", "u2")).isEmpty();
    }

    @Test
    void findDirectMessage_OtherErrorsAreFailures() {
        status = 401;

        assertThatThrownBy(() -> client().findDirectMessage("u1", "u2"))
                .isInstanceOf(MarpokatClient.MarpokatException.class);
    }

    @Test
    void setFrozen_PostsToFreezeAndDeletesToUnfreeze() {
        status = 204;
        client().setFrozen("chan-9", true);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/v1/server/channels/chan-9/freeze");

        client().setFrozen("chan-9", false);
        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(path.get()).isEqualTo("/v1/server/channels/chan-9/freeze");
    }
}
