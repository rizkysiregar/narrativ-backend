package com.rizkysiregar.narrativ.filter;


import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.service.ratelimit.RateLimiterService;
import com.rizkysiregar.narrativ.util.ClientIpResolver;
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
    private static final String PUBLIC_ENDPOINT_TYPE = "public";

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
        } else if (path(request).startsWith("/auth/")) {
            // anonymous caller on login/register/otp/...: there is no user yet, so limit per client IP
            bucket = rateLimiterService.resolveIpBucket(clientIpResolver.resolve(request), AUTH_ENDPOINT_TYPE);
        } else if (path(request).startsWith("/public/")) {
            // shared links opened without an account (and the web client's server rendering them, which passes the
            // visitor's CF-Connecting-IP along): per client IP as well, in a bucket of their own
            bucket = rateLimiterService.resolveIpBucket(clientIpResolver.resolve(request), PUBLIC_ENDPOINT_TYPE);
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
        // the id, not the username: renaming the account must not hand out a fresh bucket
        return auth.getPrincipal() instanceof UserPrincipal principal ? principal.getUserId().toString() : auth.getName();
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }

    private String resolveEndpointType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // account deletion asks for the password, so it gets the strict bucket
        if (uri.endsWith("/users/me/deletion") && "POST".equals(method)) return RateLimiterService.SENSITIVE;
        // the email change sends mails and checks the password, so it is held to the same strict bucket
        if ((uri.endsWith("/users/me/email") || uri.endsWith("/users/me/email/otp")) && "POST".equals(method)) {
            return RateLimiterService.SENSITIVE;
        }
        // the presigned upload urls have their own, higher bucket: every media of a post needs one
        if (uri.endsWith("/posts/upload-url") && "POST".equals(method)) return "post-upload";
        if (uri.contains("/posts") && "GET".equals(method)) return "feed";
        if (uri.contains("/posts") && "POST".equals(method)) return "post-create";
        if (uri.contains("/likes") || uri.contains("/comments")) return "interaction";
        return "default";
    }
}