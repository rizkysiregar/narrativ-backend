package com.rizkysiregar.narrativ.service.firebase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Picks the text of a push in the language of the device it goes to: Indonesian, or English for anything else. */
@Component
@RequiredArgsConstructor
public class PushTextResolver {

    private final MessageSource messageSource;

    /**
     * @param key  the {@code notification.<key>.title} / {@code notification.<key>.body} pair in the i18n files
     * @param args values for {@code {0}}, {@code {1}} in the body
     */
    public FirebaseNotificationService.PushText resolve(Locale locale, String key, Object... args) {
        Locale supported = "id".equals(locale.getLanguage()) ? locale : Locale.ENGLISH;
        return new FirebaseNotificationService.PushText(
                messageSource.getMessage("notification." + key + ".title", null, supported),
                messageSource.getMessage("notification." + key + ".body", args, supported));
    }
}
