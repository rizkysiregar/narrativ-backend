package com.rizkysiregar.feedzmore.i18n;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import org.springframework.context.MessageSource;
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
