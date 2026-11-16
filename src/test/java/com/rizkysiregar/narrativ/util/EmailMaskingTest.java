package com.rizkysiregar.narrativ.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailMaskingTest {

    @Test
    void keepsFirstAndLastLetterOfTheName() {
        assertEquals("r****y@example.com", EmailMasking.maskEmail("rizky@example.com"));
    }

    @Test
    void neverFailsOnOddInput() {
        assertEquals("", EmailMasking.maskEmail(null));
        assertEquals("****", EmailMasking.maskEmail("not-an-email"));
        assertEquals("****@example.com", EmailMasking.maskEmail("@example.com"));
    }
}
