package com.rizkysiregar.feedzmore.filter;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.ratelimit.RateLimiterService;
import com.rizkysiregar.feedzmore.util.ClientIpResolver;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_ENDPOINT_TYPE = "auth";

    private final RateLimiterService rateLimiterService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(RateLimiterService rateLimiterService,
                           HandlerExceptionResolver handlerExceptionResolver,
                           ClientIpResolver clientIpResolver) {
        this.rateLimiterService = rateLimiterService;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // JwtFilter already ran before this, so SecurityContext is populated
        String userId = extractUserId();

        Bucket bucket;
        if (userId != null) {
            String endpointType = resolveEndpointType(request);
            bucket = rateLimiterService.resolveBucket(userId, endpointType);
        } else if (isAuthEndpoint(request)) {
            // anonymous caller on login/register/otp/...: there is no user yet, so limit per client IP
            String ip = clientIpResolver.resolve(request);
            bucket = rateLimiterService.resolveIpBucket(ip, AUTH_ENDPOINT_TYPE);
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // Blocked — 429
        long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        log.warn("RATELIMIT.BLOCKED userId={} retryAfterSec={}", userId, retryAfter);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfter));
        handlerExceptionResolver.resolveException(
                request,
                response,
                null,
                new BusinessException(GlobalMessage.RATE_LIMIT, HttpStatus.TOO_MANY_REQUESTS, retryAfter)
        );
    }

    private String extractUserId() {
        // JwtFilter already set this — no need to re-parse the token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName(); // returns username set by JwtFilter
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return path.startsWith("/auth/");
    }

    private String resolveEndpointType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // account deletion asks for the password, so it gets the strict bucket
        if (uri.endsWith("/users/me/deletion") && "POST".equals(method)) return RateLimiterService.SENSITIVE;
        // the presigned upload urls have their own, higher bucket: every media of a post needs one
        if (uri.endsWith("/posts/upload-url") && "POST".equals(method)) return "post-upload";
        if (uri.contains("/posts") && "GET".equals(method)) return "feed";
        if (uri.contains("/posts") && "POST".equals(method)) return "post-create";
        if (uri.contains("/likes") || uri.contains("/comments")) return "interaction";
        return "default";
    }
}