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
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            apiKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (status == 200 ? "{\"id\":\"x\"}" : "{\"error\":{\"code\":\"unauthenticated\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
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
}
