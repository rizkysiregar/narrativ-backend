package com.rizkysiregar.feedzmore.filter;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.ratelimit.RateLimiterService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    private final RateLimiterService rateLimiterService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public RateLimitFilter(RateLimiterService rateLimiterService, HandlerExceptionResolver handlerExceptionResolver) {
        this.rateLimiterService = rateLimiterService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // JwtFilter already ran before this, so SecurityContext is populated
        String userId = extractUserId();

        // have no userId, skip rate limit
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String endpointType = resolveEndpointType(request);
        log.info("RATE.LIMIT.FILTER - userId: {}, endpointType: {}", userId, endpointType);

        Bucket bucket = rateLimiterService.resolveBucket(userId, endpointType);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // Blocked — 429
        long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
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
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName(); // returns username set by JwtFilter
    }

    private String resolveEndpointType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.contains("/posts") && "GET".equals(method)) return "feed";
        if (uri.contains("/posts") && "POST".equals(method)) return "post-create";
        if (uri.contains("/likes") || uri.contains("/comments")) return "interaction";
        return "default";
    }
}