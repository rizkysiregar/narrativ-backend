package com.rizkysiregar.feedzmore.i18n;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedMessagesTest {

    private static final Locale ID = Locale.of("id");

    @BeforeEach
    void wire() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        LocalizedMessages.init(source);
    }

    @AfterEach
    void unwire() {
        LocalizedMessages.init(null);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void englishIsTheDefault() {
        assertEquals("Oops, the data you are looking for was not found.",
                LocalizedMessages.resolve(GlobalMessage.NOT_FOUND, Locale.ENGLISH));
        assertEquals("Oops, the data you are looking for was not found.",
                LocalizedMessages.resolve(GlobalMessage.NOT_FOUND, Locale.FRENCH));
    }

    @Test
    void indonesianComesFromTheBundle() {
        assertEquals("Ups, data yang Anda cari tidak ditemukan.", LocalizedMessages.resolve(GlobalMessage.NOT_FOUND, ID));
    }

    @Test
    void argsAreFilledInBothLanguages() {
        assertEquals("Hang on, we are very busy right now. Please try again in 12 seconds!",
                LocalizedMessages.resolve(GlobalMessage.RATE_LIMIT, Locale.ENGLISH, 12L));
        assertEquals("Tunggu sebentar ya, lagi rame banget. Coba lagi 12 detik lagi ya!",
                LocalizedMessages.resolve(GlobalMessage.RATE_LIMIT, ID, 12L));
    }

    @Test
    void businessExceptionIsWordedInTheCurrentLanguageButLogsEnglish() {
        BusinessException e = new BusinessException(GlobalMessage.USER_BANNED_REASON, HttpStatus.UNAUTHORIZED, "spam");
        LocaleContextHolder.setLocale(ID);

        assertEquals("Akun Anda telah diblokir oleh admin. Alasan: spam", e.getLocalizedMessage());
        assertEquals("Your account has been banned by an admin. Reason: spam", e.getMessage());
        assertEquals("USER_BANNED_REASON", e.getErrorCode());
    }

    @Test
    void localeFilterDefaultsToEnglishAndHonoursIndonesian() throws Exception {
        LocaleFilter filter = new LocaleFilter(new I18nConfig().localeResolver());

        assertEquals("en", localeSeenBy(filter, null));
        assertEquals("id", localeSeenBy(filter, "id"));
        assertEquals("id", localeSeenBy(filter, "id-ID,id;q=0.9,en;q=0.8"));
        assertEquals("en", localeSeenBy(filter, "fr-FR"));
    }

    private static String localeSeenBy(LocaleFilter filter, String acceptLanguage) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(LocaleContextHolder.getLocale().getLanguage());
        filter.doFilter(request, response, chain);
        assertEquals(seen.get(), response.getHeader("Content-Language"));
        return seen.get();
    }

    @Test
    void everyMessageHasAnIndonesianTranslationAndEveryValidationKeyExistsInBothLanguages() throws Exception {
        Properties id = load("/i18n/messages_id.properties");
        Properties en = load("/i18n/messages.properties");

        for (GlobalMessage message : GlobalMessage.values()) {
            assertNotNull(id.getProperty("GlobalMessage." + message.name()), "missing id text for " + message);
        }
        for (String key : id.stringPropertyNames()) {
            if (key.startsWith("validation.")) {
                assertTrue(en.containsKey(key), "validation key only in id: " + key);
            }
        }
        for (String key : en.stringPropertyNames()) {
            assertTrue(id.containsKey(key), "validation key missing in id: " + key);
        }
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (var in = new InputStreamReader(LocalizedMessagesTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        return properties;
    }
}
