package com.rizkysiregar.narrativ.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.narrativ.config.AppCheckProperties;
import com.rizkysiregar.narrativ.config.AppCheckProperties.Mode;
import com.rizkysiregar.narrativ.config.AppCheckProperties.Rule;
import com.rizkysiregar.narrativ.service.appcheck.AppCheckTokenVerifier;
import com.rizkysiregar.narrativ.service.appcheck.AppCheckTokenVerifier.InvalidTokenException;
import com.rizkysiregar.narrativ.service.appcheck.AppCheckTokenVerifier.UnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppCheckFilterTest {

    private static final String HEADER = "X-Firebase-AppCheck";
    private static final String APP_ID = "1:1:android:abc";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private int verifyCalls;

    private AppCheckTokenVerifier acceptsOnly(String goodToken) {
        return token -> {
            verifyCalls++;
            if (token.equals(goodToken)) return APP_ID;
            throw new InvalidTokenException("bad token", null);
        };
    }

    private AppCheckFilter filter(Mode mode, Rule rule, AppCheckTokenVerifier verifier) {
        AppCheckProperties properties = new AppCheckProperties(mode, "123", HEADER, List.of(rule));
        return new AppCheckFilter(properties, verifier, new ObjectMapper(), meters);
    }

    private static Rule rule(Mode mode, String... apps) {
        return new Rule(List.of("/auth/login", "/posts/**"), List.of(apps), mode);
    }

    private MockHttpServletResponse run(AppCheckFilter filter, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest post(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) request.addHeader(HEADER, token);
        return request;
    }

    private double count(String outcome, String mode) {
        var counter = meters.find("narrativ.appcheck.requests").tag("outcome", outcome).tag("mode", mode).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void offChecksNothing() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.OFF, rule(null), acceptsOnly("good")), post("/auth/login", null), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(0, verifyCalls);
    }

    @Test
    void aPathNoRuleCoversIsNeverChecked() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")), post("/admin/users", null), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(0, verifyCalls);
    }

    @Test
    void aCorsPreflightIsNeverChecked() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")),
                new MockHttpServletRequest("OPTIONS", "/auth/login"), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void enforceRefusesAMissingTokenWith403NotUnauthorized() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")), post("/auth/login", null), chain);

        assertEquals(403, response.getStatus());
        assertEquals("missing", response.getHeader(AppCheckFilter.RESULT_HEADER));
        assertTrue(response.getContentAsString().contains("\"code\":\"403\""));
        assertNull(chain.getRequest(), "the request must not reach the controller");
        assertEquals(1, count("missing", "enforce"));
    }

    @Test
    void enforceRefusesAnInvalidToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")), post("/auth/login", "forged"), chain);

        assertEquals(403, response.getStatus());
        assertEquals("invalid", response.getHeader(AppCheckFilter.RESULT_HEADER));
        assertNull(chain.getRequest());
    }

    @Test
    void enforceLetsAValidTokenThroughAndExposesTheAppId() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")), post("/auth/login", "good"), chain);

        assertEquals(200, response.getStatus());
        assertEquals(APP_ID, chain.getRequest().getAttribute(AppCheckFilter.APP_ID_ATTRIBUTE));
        assertEquals(1, count("ok", "enforce"));
    }

    @Test
    void enforceRefusesAnAppThatTheRuleDoesNotAllow() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(
                filter(Mode.ENFORCE, rule(null, "1:1:android:other"), acceptsOnly("good")), post("/auth/login", "good"), chain);

        assertEquals(403, response.getStatus());
        assertEquals("not_allowed", response.getHeader(AppCheckFilter.RESULT_HEADER));
    }

    @Test
    void anAllowedAppPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(
                filter(Mode.ENFORCE, rule(null, APP_ID), acceptsOnly("good")), post("/auth/login", "good"), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void logModeReportsButNeverRefuses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.LOG, rule(null), acceptsOnly("good")), post("/auth/login", null), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(1, count("missing", "log"));
    }

    @Test
    void aRuleCanBeMilderThanTheGlobalMode() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(Mode.LOG), acceptsOnly("good")), post("/posts/1", null), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(1, count("missing", "log"));
    }

    @Test
    void aRuleCannotBeStricterThanTheGlobalMode() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.LOG, rule(Mode.ENFORCE), acceptsOnly("good")), post("/posts/1", null), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void whenGoogleKeysCannotBeFetchedTheRequestIsLetThrough() throws Exception {
        AppCheckTokenVerifier down = token -> {
            throw new UnavailableException("keys unreachable", null);
        };
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), down), post("/auth/login", "anything"), chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(1, count("unavailable", "enforce"));
    }

    @Test
    void theContextPathIsIgnoredWhenMatching() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/login", null);
        request.setContextPath("/api/v1");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter(Mode.ENFORCE, rule(null), acceptsOnly("good")), request, chain);

        assertEquals(403, response.getStatus());
    }
}
