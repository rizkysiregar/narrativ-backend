package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankedCursorTest {

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTrips() {
        RankedCursor cursor = new RankedCursor(UUID.randomUUID(), 20, LocalDateTime.of(2026, 11, 11, 10, 0, 0, 123456000));

        assertEquals(Optional.of(cursor), RankedCursor.tryDecode(cursor.encode()));
    }

    @Test
    void aDateCursorIsNotRanked() {
        FeedCursor dateCursor = new FeedCursor(LocalDateTime.now(), UUID.randomUUID());

        assertEquals(Optional.empty(), RankedCursor.tryDecode(dateCursor.encode()));
    }

    @Test
    void aMangledRankedCursorIsABadRequest() {
        for (String bad : new String[]{"r|nope|1|2026-11-11T10:00", "r|" + UUID.randomUUID() + "|-1|2026-11-11T10:00",
                "r|" + UUID.randomUUID() + "|1", "r|" + UUID.randomUUID() + "|1|2026-11-11T10:00|x"}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> RankedCursor.tryDecode(encode(bad)), bad);
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
        assertThrows(BusinessException.class, () -> RankedCursor.tryDecode("not base64 !"));
    }

    @Test
    void afterTheRankingTheFeedGoesOnStrictlyBelowItsWindow() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 11, 10, 0);

        assertEquals(new FeedCursor(windowStart, new UUID(0L, 0L)),
                new RankedCursor(UUID.randomUUID(), 3, windowStart).afterRanking());
    }
}
