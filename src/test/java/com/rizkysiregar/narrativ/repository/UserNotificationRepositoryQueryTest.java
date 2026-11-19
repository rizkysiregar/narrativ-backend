package com.rizkysiregar.narrativ.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native inbox queries are glued from text blocks, and a text block drops the spaces at the end of a line: a
 * keyword then runs into what follows ("HAVINGCAST(", a 500 on dev). This reads the compiled SQL and fails on any
 * word directly followed by "(" that is not a known function.
 */
class UserNotificationRepositoryQueryTest {

    private static final Set<String> FUNCTIONS = Set.of("CAST", "EXTRACT", "MAX", "COUNT", "BOOL_OR", "ARRAY_AGG");
    private static final Pattern WORD_THEN_PAREN = Pattern.compile("([A-Za-z_]+)\\(");

    private static String sqlOf(String method) {
        for (Method m : UserNotificationRepository.class.getMethods()) {
            if (m.getName().equals(method)) {
                return m.getAnnotation(Query.class).value();
            }
        }
        throw new IllegalArgumentException(method);
    }

    private static void assertNoGluedKeyword(String sql) {
        Matcher matcher = WORD_THEN_PAREN.matcher(sql);
        while (matcher.find()) {
            String word = matcher.group(1);
            assertTrue(FUNCTIONS.contains(word.toUpperCase()), () -> "\"" + word + "(\" in:\n" + sql);
        }
    }

    @Test
    void theInboxPageQueryHasNoKeywordGluedToWhatFollows() {
        assertNoGluedKeyword(sqlOf("findGroups"));
    }

    @Test
    void theUnreadCountQueryHasNoKeywordGluedToWhatFollows() {
        assertNoGluedKeyword(sqlOf("countUnreadGroups"));
    }
}
