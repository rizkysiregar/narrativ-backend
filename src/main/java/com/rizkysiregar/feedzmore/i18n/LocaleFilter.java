package com.rizkysiregar.feedzmore.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;

/**
 * Picks the language from {@code Accept-Language} (English or Indonesian, English when absent or unsupported) before
 * the security filters run, so the error bodies they write are localized too. Spring's own request context filter
 * (order -105) sets the raw, unrestricted request locale, so this one runs right after it.
 */
@Component
@Order(-104)
@RequiredArgsConstructor
public class LocaleFilter extends OncePerRequestFilter {

    private final LocaleResolver localeResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
        response.setHeader("Content-Language", LocaleContextHolder.getLocale().getLanguage());
        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
