package com.rizkysiregar.feedzmore.filter;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.service.ratelimit.RateLimiterService;
import com.rizkysiregar.feedzmore.util.ClientIpResolver;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final int LIMIT = 2;

    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    private RateLimitFilter filter;
    // one real in-memory bucket per key, so limits behave like production without Redis
    private final Map<String, Bucket> buckets = new HashMap<>();

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiterService, handlerExceptionResolver, new ClientIpResolver("CF-Connecting-IP"));
        lenient().when(rateLimiterService.resolveIpBucket(anyString(), anyString()))
                .thenAnswer(inv -> bucket("ip:" + inv.getArgument(0) + ":" + inv.getArgument(1)));
        lenient().when(rateLimiterService.resolveBucket(anyString(), anyString()))
                .thenAnswer(inv -> bucket("user:" + inv.getArgument(0) + ":" + inv.getArgument(1)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Bucket bucket(String key) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(LIMIT).refillGreedy(LIMIT, Duration.ofMinutes(1)))
                .build());
    }

    private MockHttpServletRequest request(String path, String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1" + path);
        request.setContextPath("/api/v1");
        request.setRemoteAddr("10.42.0.7"); // the proxy, identical for everybody
        if (clientIp != null) {
            request.addHeader("CF-Connecting-IP", clientIp);
        }
        return request;
    }

    private MockFilterChain call(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain;
    }

    @Test
    void anonymousAuthEndpoint_IsLimitedPerClientIp() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = call(request("/auth/login", "203.0.113.9"), response);
            assertNotNull(chain.getRequest(), "request " + i + " should pass");
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        MockFilterChain chain = call(request("/auth/login", "203.0.113.9"), blocked);

        assertNull(chain.getRequest(), "request over the limit must not reach the controller");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
        assertNotNull(blocked.getHeader("X-Rate-Limit-Retry-After-Seconds"));
        ArgumentCaptor<Exception> error = ArgumentCaptor.forClass(Exception.class);
        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), error.capture());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ((BusinessException) error.getValue()).getHttpStatus());
        verify(rateLimiterService, never()).resolveBucket(anyString(), anyString());
    }

    @Test
    void anonymousAuthEndpoints_ShareOneBucketPerIp() throws Exception {
        // login + forgot-password + verify-otp must not each get their own fresh budget
        call(request("/auth/login", "203.0.113.9"), new MockHttpServletResponse());
        call(request("/auth/forgot-password", "203.0.113.9"), new MockHttpServletResponse());

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        MockFilterChain chain = call(request("/auth/verify-otp", "203.0.113.9"), blocked);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
    }

    @Test
    void anonymousAuthEndpoint_DifferentIpsDoNotAffectEachOther() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            call(request("/auth/login", "203.0.113.9"), new MockHttpServletResponse());
        }

        MockHttpServletResponse other = new MockHttpServletResponse();
        MockFilterChain chain = call(request("/auth/login", "198.51.100.4"), other);

        assertNotNull(chain.getRequest());
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), other.getStatus());
    }

    @Test
    void anonymousAuthEndpoint_WithoutIpHeader_FallsBackToSocketAddress() throws Exception {
        call(request("/auth/login", null), new MockHttpServletResponse());

        verify(rateLimiterService).resolveIpBucket("10.42.0.7", "auth");
    }

    @Test
    void anonymousNonAuthEndpoint_IsNotLimited() throws Exception {
        for (String path : List.of("/res/photo", "/hello", "/actuator/health", "/v3/api-docs")) {
            MockFilterChain chain = call(request(path, "203.0.113.9"), new MockHttpServletResponse());
            assertNotNull(chain.getRequest(), path);
        }

        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void anonymousAuthenticationToken_IsTreatedAsAnonymous_NotAsOneSharedUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        call(request("/auth/login", "203.0.113.9"), new MockHttpServletResponse());

        verify(rateLimiterService).resolveIpBucket("203.0.113.9", "auth");
        verify(rateLimiterService, never()).resolveBucket(anyString(), anyString());
    }

    @Test
    void authenticatedUser_StillLimitedPerUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("rizky", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        MockHttpServletRequest feed = new MockHttpServletRequest("GET", "/api/v1/posts");
        feed.setContextPath("/api/v1");
        MockFilterChain chain = call(feed, new MockHttpServletResponse());

        assertNotNull(chain.getRequest());
        verify(rateLimiterService).resolveBucket("rizky", "feed");
        verify(rateLimiterService, never()).resolveIpBucket(anyString(), anyString());
    }

    @Test
    void uploadUrl_UsesItsOwnBucketSoMediaUploadsDoNotEatThePostCreateBudget() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("rizky", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        call(request("/posts/upload-url", null), new MockHttpServletResponse());
        call(request("/posts", null), new MockHttpServletResponse());

        verify(rateLimiterService).resolveBucket("rizky", "post-upload");
        verify(rateLimiterService).resolveBucket("rizky", "post-create");
    }

    @Test
    void accountDeletion_UsesTheStrictBucketNotTheDefaultOne() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("rizky", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        call(request("/users/me/deletion", null), new MockHttpServletResponse());

        verify(rateLimiterService).resolveBucket("rizky", "sensitive");
        verify(rateLimiterService, never()).resolveBucket("rizky", "default");
    }
}
