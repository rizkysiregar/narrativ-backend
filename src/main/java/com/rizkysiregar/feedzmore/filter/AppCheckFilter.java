package com.rizkysiregar.feedzmore.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.config.AppCheckProperties;
import com.rizkysiregar.feedzmore.config.AppCheckProperties.Mode;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;
import com.rizkysiregar.feedzmore.service.appcheck.AppCheckTokenVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Lets only the genuine mobile app through on the paths listed in {@code app.appcheck.rules}: the app proves
 * itself with Play Integrity, Firebase turns that into a short lived token, and the app sends it in a header.
 * <p>
 * It answers 403, never 401: clients treat a 401 as an expired login, so they would try to refresh the token
 * and, failing that, sign the user out. When Google's keys cannot be fetched the request is let through (and
 * counted as {@code unavailable}) rather than turning a Google outage into ours.
 * <p>
 * Every checked request is counted in {@code feedzmore.appcheck.requests} by outcome and mode, which is how
 * to tell, in {@code log} mode, when it is safe to switch to {@code enforce}.
 */
@Slf4j
@Component
public class AppCheckFilter extends OncePerRequestFilter {

    /** Request attribute with the Firebase app id of a verified token. */
    public static final String APP_ID_ATTRIBUTE = "appCheck.appId";
    public static final String RESULT_HEADER = "X-AppCheck-Result";

    enum Outcome {
        OK, MISSING, INVALID, NOT_ALLOWED, UNAVAILABLE;

        String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final AppCheckProperties properties;
    private final AppCheckTokenVerifier verifier;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AppCheckFilter(AppCheckProperties properties, AppCheckTokenVerifier verifier,
                          ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // a browser preflight never carries the header; CORS is answered before anything else
        if (properties.mode() == Mode.OFF || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = pathWithoutContext(request);
        var rule = properties.ruleFor(path);
        if (rule.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Mode mode = rule.get().mode() == null
                ? properties.mode()
                : rule.get().mode().capAt(properties.mode());
        if (mode == Mode.OFF) {
            filterChain.doFilter(request, response);
            return;
        }

        String[] appId = new String[1];
        Outcome outcome = check(request, rule.get().allowedApps(), appId);
        meterRegistry.counter("feedzmore.appcheck.requests",
                "outcome", outcome.tag(), "mode", mode.name().toLowerCase(Locale.ROOT)).increment();

        if (outcome == Outcome.OK) {
            request.setAttribute(APP_ID_ATTRIBUTE, appId[0]);
        }
        if (outcome == Outcome.OK || outcome == Outcome.UNAVAILABLE) {
            filterChain.doFilter(request, response);
            return;
        }

        if (mode == Mode.LOG) {
            log.warn("APPCHECK.WOULD.REFUSE method={} path={} outcome={}", request.getMethod(), path, outcome.tag());
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("APPCHECK.REFUSED method={} path={} outcome={}", request.getMethod(), path, outcome.tag());
        refuse(response, outcome);
    }

    private Outcome check(HttpServletRequest request, java.util.List<String> allowedApps, String[] appIdOut) {
        String token = request.getHeader(properties.header());
        if (token == null || token.isBlank()) {
            return Outcome.MISSING;
        }
        try {
            String appId = verifier.verify(token.trim());
            if (!allowedApps.isEmpty() && !allowedApps.contains(appId)) {
                return Outcome.NOT_ALLOWED;
            }
            appIdOut[0] = appId;
            return Outcome.OK;
        } catch (AppCheckTokenVerifier.InvalidTokenException e) {
            return Outcome.INVALID;
        } catch (AppCheckTokenVerifier.UnavailableException e) {
            log.error("APPCHECK.UNAVAILABLE the token could not be checked, letting the request through: {}",
                    e.getMessage());
            return Outcome.UNAVAILABLE;
        }
    }

    private void refuse(HttpServletResponse response, Outcome outcome) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(RESULT_HEADER, outcome.tag());
        objectMapper.writeValue(response.getWriter(), BaseResponse.builder()
                .code(GlobalMessage.APP_CHECK_FAILED.getCode())
                .message(GlobalMessage.APP_CHECK_FAILED.getMessage())
                .build());
    }

    private static String pathWithoutContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }
}
