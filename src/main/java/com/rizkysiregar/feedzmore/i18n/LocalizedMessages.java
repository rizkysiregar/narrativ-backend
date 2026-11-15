package com.rizkysiregar.feedzmore.i18n;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;

/**
 * Turns a {@link GlobalMessage} into text in the language of the current request ({@link LocaleContextHolder}, set by
 * {@link LocaleFilter}). English is the enum's own text; other languages come from {@code i18n/messages_<lang>.properties}.
 * <p>
 * Static on purpose: the servlet filters and exception handlers that write error bodies are built by hand in many
 * places. Until Spring wires the {@link MessageSource} (e.g. in plain unit tests) it falls back to English.
 */
public final class LocalizedMessages {

    private static volatile MessageSource messageSource;

    private LocalizedMessages() {
    }

    static void init(MessageSource source) {
        messageSource = source;
    }

    public static String resolve(GlobalMessage message, Object... args) {
        return resolve(message, LocaleContextHolder.getLocale(), args);
    }

    public static String resolve(GlobalMessage message, Locale locale, Object... args) {
        MessageSource source = messageSource;
        if (source == null) {
            return english(message, args);
        }
        return source.getMessage("GlobalMessage." + message.name(), args, english(message, args), locale);
    }

    /** Free text (emails) by key; {@code defaultText} is the English text and is used when the bundle has no entry. */
    public static String text(String key, Locale locale, String defaultText, Object... args) {
        MessageSource source = messageSource;
        if (source == null) {
            return args == null || args.length == 0 ? defaultText : MessageFormat.format(defaultText, args);
        }
        return source.getMessage(key, args, defaultText, locale);
    }

    /**
     * The supported language for a stored language tag such as "id-ID", "in_ID" (old Android code for Indonesian) or
     * "en-US". Anything else, or nothing, is English.
     */
    public static Locale supportedLocale(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return I18nConfig.ENGLISH;
        }
        String language = languageTag.trim().toLowerCase(Locale.ROOT).split("[-_]")[0];
        return language.equals("id") || language.equals("in") ? I18nConfig.INDONESIAN : I18nConfig.ENGLISH;
    }

    /** Runs {@code action} with {@code locale} as the current language (consumers have no request to take it from). */
    public static void withLocale(Locale locale, Runnable action) {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(locale);
        try {
            action.run();
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }

    /** The default (English) text, with the args filled in. */
    public static String english(GlobalMessage message, Object... args) {
        return args == null || args.length == 0
                ? message.getMessage()
                : MessageFormat.format(message.getMessage(), args);
    }

    public static String[] stringArgs(Object... args) {
        return args == null ? new String[0] : Arrays.stream(args).map(String::valueOf).toArray(String[]::new);
    }
}
