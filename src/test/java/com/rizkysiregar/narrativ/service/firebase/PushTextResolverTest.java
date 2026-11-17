package com.rizkysiregar.narrativ.service.firebase;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real i18n files, so a missing key or a bad escape in them fails here. */
class PushTextResolverTest {

    private final PushTextResolver resolver = new PushTextResolver(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void everyPushHasATitleAndBodyInBothLanguagesWithTheArgumentsFilledIn() {
        for (String key : List.of("newPost", "postLiked", "postCommented", "commentReplied")) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.of("id"))) {
                FirebaseNotificationService.PushText text = resolver.resolve(locale, key, "budi", "nice one");
                assertFalse(text.title().isBlank(), key + " title " + locale);
                assertTrue(text.body().contains("budi"), key + " body " + locale + ": " + text.body());
            }
        }
    }

    @Test
    void anyLanguageButIndonesianGetsEnglish() {
        assertEquals(resolver.resolve(Locale.ENGLISH, "postLiked", "budi"),
                resolver.resolve(Locale.FRENCH, "postLiked", "budi"));
        assertEquals(resolver.resolve(Locale.of("id"), "postLiked", "budi"),
                resolver.resolve(Locale.forLanguageTag("id-ID"), "postLiked", "budi"));
    }

    @Test
    void commentTextsShowThePreview() {
        assertTrue(resolver.resolve(Locale.of("id"), "postCommented", "budi", "mantap").body().endsWith("mantap"));
        assertTrue(resolver.resolve(Locale.ENGLISH, "commentReplied", "budi", "mantap").body().endsWith("mantap"));
    }
}
