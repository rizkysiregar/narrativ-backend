package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchTermTest {

    @Test
    void isTrimmedAndLowerCased() {
        assertEquals("coffee", SearchTerm.of("  Coffee ").value());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "a", "  b  ", "123456789012345678901234567890123456789012345678901"})
    void tooShortOrTooLongIsABadRequest(String raw) {
        BusinessException ex = assertThrows(BusinessException.class, () -> SearchTerm.of(raw));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void likePatternsMatchWildcardsLiterally() {
        SearchTerm term = SearchTerm.of("50%_off!");
        assertEquals("%50!%!_off!!%", term.containsPattern());
        assertEquals("50!%!_off!!%", term.prefixPattern());
    }

    @Test
    void everyWordBecomesARequiredPrefix() {
        assertEquals("kopi:* & sus:*", SearchTerm.of("Kopi  sus").prefixTsQuery());
    }

    @Test
    void hashtagsAndPunctuationAreOnlySeparators() {
        assertEquals("coffee:* & code:*", SearchTerm.of("#coffee & code!").prefixTsQuery());
    }

    @Test
    void tsQuerySyntaxTypedByTheUserIsDropped() {
        assertEquals("a:* & b:* & c:*", SearchTerm.of("a:*|b<->!c").prefixTsQuery());
    }

    @Test
    void lettersOfAnyScriptAreWords() {
        assertEquals("café:* & 東京:*", SearchTerm.of("café 東京").prefixTsQuery());
    }

    @Test
    void onlyPunctuationHasNoWord() {
        assertEquals("", SearchTerm.of("?!#").prefixTsQuery());
    }

    @Test
    void atMostEightWords() {
        assertEquals("a1:* & a2:* & a3:* & a4:* & a5:* & a6:* & a7:* & a8:*",
                SearchTerm.of("a1 a2 a3 a4 a5 a6 a7 a8 a9 a10").prefixTsQuery());
    }
}
