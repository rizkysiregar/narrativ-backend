package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InboxCursorTest {

    @Test
    void aCursorSurvivesTheRoundTrip() {
        InboxCursor cursor = new InboxCursor(1_763_550_000_123_456L, "like:abc:2026-11-19");

        assertEquals(cursor, InboxCursor.decode(cursor.encode()));
    }

    @Test
    void noCursorIsTheFirstPage() {
        assertEquals(InboxCursor.FIRST, InboxCursor.decode(null));
        assertEquals(InboxCursor.FIRST, InboxCursor.decode(" "));
    }

    @Test
    void aMangledCursorIsABadRequest() {
        assertThrows(BusinessException.class, () -> InboxCursor.decode("not-a-cursor"));
    }
}
