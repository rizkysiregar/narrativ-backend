package com.rizkysiregar.narrativ.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rizkysiregar.narrativ.util.ClientPlatform;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One access log line per request, written when the request finishes:
 * {@code HTTP.REQUEST id= method= path= status= durationMs= ip= userId= client= body=}.
 * {@code client} is the self-declared {@code platform/version} of the caller (see {@link ClientPlatform}).
 * <p>
 * The headers and the forwarding chain are only added to requests that failed (status 400+), where they help to
 * debug. {@code app.logging.request-body} decides when the (redacted) JSON body is included:
 * {@code ALWAYS}, {@code ERRORS} (status 400+ only) or {@code NEVER}.
 * {@code requestId} and {@code userId} are also put in the MDC, so every other log line of the request carries them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String USER_ID_MDC_KEY = "userId";

    private static final int MAX_BODY_BYTES = 8 * 1024;
    private static final int MAX_HEADER_VALUE_LENGTH = 512;
    private static final long SLOW_REQUEST_MS = 2_000;

    public enum BodyMode { ALWAYS, ERRORS, NEVER }

    private final ObjectMapper objectMapper;
    private final BodyMode bodyMode;

    public RequestTraceFilter(ObjectMapper objectMapper,
                              @Value("${app.logging.request-body:ERRORS}") BodyMode bodyMode) {
        this.objectMapper = objectMapper;
        this.bodyMode = bodyMode;
    }

    // health probes and metrics scrapes would otherwise be the most frequent lines in the log
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
        String requestId = requestId(request.getHeader("X-Request-ID"));
        long startedAt = System.nanoTime();
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            logRequest(wrappedRequest, response, requestId, (System.nanoTime() - startedAt) / 1_000_000);
            MDC.remove(USER_ID_MDC_KEY);
            MDC.remove("requestId");
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, HttpServletResponse response,
                            String requestId, long durationMs) {
        int status = response.getStatus();
        boolean failed = status >= 400;
        boolean withBody = bodyMode == BodyMode.ALWAYS || (bodyMode == BodyMode.ERRORS && failed);
        String userId = MDC.get(USER_ID_MDC_KEY);
        String body = withBody ? body(request) : "[not logged]";
        String query = sanitizeQuery(request.getQueryString());
        String client = ClientPlatform.describe(request);

        if (failed) {
            String message = "HTTP.REQUEST id={} method={} path={} query={} status={} durationMs={} ip={} userId={} client={} body={} "
                    + "forwardedFor={} realIp={} forwarded={} headers={}";
            Object[] args = {requestId, request.getMethod(), safe(request.getRequestURI(), 1024), query, status,
                    durationMs, safe(request.getRemoteAddr(), 128), userId, client, body,
                    safe(request.getHeader("X-Forwarded-For"), MAX_HEADER_VALUE_LENGTH),
                    safe(request.getHeader("X-Real-IP"), MAX_HEADER_VALUE_LENGTH),
                    safe(request.getHeader("Forwarded"), MAX_HEADER_VALUE_LENGTH), headers(request)};
            if (status >= 500) {
                log.error(message, args);
            } else {
                log.warn(message, args);
            }
            return;
        }

        Object[] args = {requestId, request.getMethod(), safe(request.getRequestURI(), 1024), query, status,
                durationMs, safe(request.getRemoteAddr(), 128), userId, client, body};
        if (durationMs >= SLOW_REQUEST_MS) {
            log.warn("HTTP.REQUEST.SLOW id={} method={} path={} query={} status={} durationMs={} ip={} userId={} client={} body={}", args);
        } else {
            log.info("HTTP.REQUEST id={} method={} path={} query={} status={} durationMs={} ip={} userId={} client={} body={}", args);
        }
    }

    private String requestId(String suppliedId) {
        return suppliedId != null && suppliedId.matches("[A-Za-z0-9._-]{1,80}")
                ? suppliedId : UUID.randomUUID().toString();
    }

    private Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (request.getHeaderNames() == null) {
            return result;
        }
        for (String name : Collections.list(request.getHeaderNames())) {
            List<String> values = Collections.list(request.getHeaders(name)).stream()
                    .map(value -> sensitive(name) ? "[REDACTED]" : safe(value, MAX_HEADER_VALUE_LENGTH))
                    .toList();
            result.put(name, values);
        }
        return result;
    }

    private String body(ContentCachingRequestWrapper request) {
        byte[] bytes = request.getContentAsByteArray();
        if (bytes.length == 0) {
            return "[empty or unread]";
        }
        String contentType = request.getContentType();
        if (contentType == null || !isJson(contentType)) {
            return "[omitted: non-JSON body]";
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES || bytes.length >= MAX_BODY_BYTES) {
            return "[omitted: body exceeds 8 KB logging limit]";
        }
        try {
            JsonNode payload = objectMapper.readTree(bytes);
            redact(payload);
            return payload.toString();
        } catch (Exception e) {
            return "[unparseable JSON]";
        }
    }

    private boolean isJson(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("application/json") || normalized.contains("+json");
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
                if (sensitive(entry.getKey())) {
                    object.put(entry.getKey(), "[REDACTED]");
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::redact);
        }
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String rawName = separator < 0 ? part : part.substring(0, separator);
            String name;
            try {
                name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                name = rawName;
            }
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(safe(rawName, 128));
            if (separator >= 0) {
                result.append('=').append(sensitive(name) ? "[REDACTED]" : safe(part.substring(separator + 1), 512));
            }
        }
        return safe(result.toString(), 1024);
    }

    private boolean sensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("password") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("authorization")
                || normalized.contains("cookie") || normalized.contains("apikey")
                || normalized.contains("credential") || normalized.contains("otp")
                || normalized.contains("email");
    }

    private String safe(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", " ");
        return normalized.length() > limit ? normalized.substring(0, limit) + "..." : normalized;
    }
}
