package com.rizkysiregar.feedzmore.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostFeedResponseTest {

    private PostFeedResponse response(LocalDateTime createdAt) {
        return new PostFeedResponse(UUID.randomUUID(), "author", "caption", List.of(), createdAt, 0, 0, false);
    }

    @Test
    void createdAt_isTheMomentTheJvmZoneWroteNotTheRawLocalTime() {
        LocalDateTime stored = LocalDateTime.of(2026, 11, 11, 3, 31, 6);

        assertEquals(stored.atZone(ZoneId.systemDefault()).toInstant(), response(stored).getCreatedAt());
    }

    @Test
    void createdAt_isSerializedWithAZoneDesignator() throws Exception {
        PostFeedResponse response = response(LocalDateTime.of(2026, 11, 11, 3, 31, 6));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String createdAt = mapper.readTree(mapper.writeValueAsString(response)).get("createdAt").asText();

        assertEquals(response.getCreatedAt(), Instant.parse(createdAt));
    }

    @Test
    void missingCreatedAtStaysNull() {
        assertNull(response(null).getCreatedAt());
    }
}
