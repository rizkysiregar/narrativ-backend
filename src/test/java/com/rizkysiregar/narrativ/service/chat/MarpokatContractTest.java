package com.rizkysiregar.narrativ.service.chat;

import com.rizkysiregar.narrativ.config.MarpokatProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks against a real Marpokat that the tokens signed here (jjwt) are accepted by the Go server: a server token
 * for the upsert, then a user token for GET /v1/me. Skipped unless MARPOKAT_CONTRACT_BASE_URL, _API_KEY and
 * _API_SECRET are set, e.g. against a local Marpokat (marpokat/server: make deps-up run-api, marpokat tenant create).
 */
class MarpokatContractTest {

    @Test
    void tokensSignedByNarrativAreAcceptedByMarpokat() throws Exception {
        String baseUrl = System.getenv("MARPOKAT_CONTRACT_BASE_URL");
        String apiKey = System.getenv("MARPOKAT_CONTRACT_API_KEY");
        String secret = System.getenv("MARPOKAT_CONTRACT_API_SECRET");
        assumeTrue(baseUrl != null && apiKey != null && secret != null, "MARPOKAT_CONTRACT_* not set");

        MarpokatClient client = new MarpokatClient(new MarpokatProperties(baseUrl, apiKey, secret, Duration.ofMinutes(5)),
                Clock.systemUTC());
        String externalId = "contract-" + UUID.randomUUID();
        client.upsertUser(externalId, new MarpokatClient.UserProfile("Contract Test", null, Map.of("username", "contract")));

        HttpResponse<String> me = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/me"))
                .header("X-Api-Key", apiKey)
                .header("Authorization", "Bearer " + client.userToken(externalId).token())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains("\"external_id\":\"" + externalId + "\"").contains("\"name\":\"Contract Test\"");
    }
}
