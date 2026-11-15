package com.rizkysiregar.feedzmore.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/** Supported API languages: English (default) and Indonesian. */
@Configuration
public class I18nConfig {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale INDONESIAN = Locale.of("id");

    // the bean name is what DispatcherServlet looks up
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(ENGLISH, INDONESIAN));
        resolver.setDefaultLocale(ENGLISH);
        return resolver;
    }

    @Autowired
    void registerMessageSource(MessageSource messageSource) {
        LocalizedMessages.init(messageSource);
    }
}
