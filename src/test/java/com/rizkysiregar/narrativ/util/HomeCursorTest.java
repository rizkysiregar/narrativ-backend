package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HomeCursorTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 20, 10, 0, 0, 123_000);

    private static String raw(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void everyStateSurvivesTheRoundTrip() {
        FeedCursor following = new FeedCursor(AT, UUID.randomUUID());
        RankedCursor ranking = new RankedCursor(UUID.randomUUID(), 6, AT.minusDays(7));
        for (HomeCursor cursor : new HomeCursor[]{
                new HomeCursor(following, HomeCursor.Suggestions.NOT_STARTED, 4),
                new HomeCursor(following, HomeCursor.Suggestions.at(ranking), 11),
                new HomeCursor(following, HomeCursor.Suggestions.DONE, 0),
                new HomeCursor(null, HomeCursor.Suggestions.at(ranking), 0),
                new HomeCursor(null, HomeCursor.Suggestions.DONE, 0)}) {
            assertEquals(cursor, HomeCursor.decode(cursor.encode()));
        }
    }

    @Test
    void aDateCursorOfTheOldHomeFeedIsInTheFollowedPosts() {
        FeedCursor old = new FeedCursor(AT, UUID.randomUUID());
        assertEquals(new HomeCursor(old, HomeCursor.Suggestions.NOT_STARTED, 0), HomeCursor.decode(old.encode()));
    }

    @Test
    void aRankedCursorOfTheOldHomeFeedIsInTheSuggestions() {
        RankedCursor old = new RankedCursor(UUID.randomUUID(), 10, AT);
        assertEquals(new HomeCursor(null, HomeCursor.Suggestions.at(old), 0), HomeCursor.decode(old.encode()));
    }

    @Test
    void aMangledCursorIsABadRequest() {
        for (String cursor : new String[]{"%%%", raw("h|x|x"), raw("h|x|x|-1"), raw("h|nope|x|0"),
                raw("h|x|" + UUID.randomUUID() + ",-1," + AT + "|0"), raw("h|x|a,b|0"), raw("garbage")}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> HomeCursor.decode(cursor), cursor);
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
    }
}
