package com.rizkysiregar.narrativ.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver("CF-Connecting-IP");

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void resolve_UsesConfiguredHeader() {
        MockHttpServletRequest request = request("10.42.0.7");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");

        assertEquals("203.0.113.9", resolver.resolve(request));
    }

    @Test
    void resolve_TakesFirstEntryOfCommaSeparatedHeader() {
        ClientIpResolver xff = new ClientIpResolver("X-Forwarded-For");
        MockHttpServletRequest request = request("10.42.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        assertEquals("203.0.113.9", xff.resolve(request));
    }

    @Test
    void resolve_FallsBackToSocketAddress_WhenHeaderMissing() {
        assertEquals("10.42.0.7", resolver.resolve(request("10.42.0.7")));
    }

    @Test
    void resolve_FallsBackToSocketAddress_WhenHeaderIsNotAnIp() {
        // must never turn arbitrary text into a Redis key, or a hostname into a DNS lookup
        for (String junk : new String[]{"evil.example.com", "dead.beef", "999.1.1.1", "1.2.3", "' OR 1=1 --", ":::::"}) {
            MockHttpServletRequest request = request("10.42.0.7");
            request.addHeader("CF-Connecting-IP", junk);

            assertEquals("10.42.0.7", resolver.resolve(request), "header value: " + junk);
        }
    }

    @Test
    void resolve_BlankHeaderConfig_IgnoresHeaders() {
        ClientIpResolver socketOnly = new ClientIpResolver("");
        MockHttpServletRequest request = request("10.42.0.7");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");

        assertEquals("10.42.0.7", socketOnly.resolve(request));
    }

    @Test
    void resolve_Ipv6_SameSlash64SharesOneKey() {
        MockHttpServletRequest first = request("10.42.0.7");
        first.addHeader("CF-Connecting-IP", "2001:db8:abcd:12::1");
        MockHttpServletRequest second = request("10.42.0.7");
        second.addHeader("CF-Connecting-IP", "2001:0db8:abcd:0012:ffff:ffff:ffff:ffff");
        MockHttpServletRequest otherNetwork = request("10.42.0.7");
        otherNetwork.addHeader("CF-Connecting-IP", "2001:db8:abcd:13::1");

        assertEquals("2001:0db8:abcd:0012::/64", resolver.resolve(first));
        assertEquals(resolver.resolve(first), resolver.resolve(second));
        assertNotEquals(resolver.resolve(first), resolver.resolve(otherNetwork));
    }

    @Test
    void resolve_Ipv4MappedIpv6_IsNormalizedToIpv4() {
        MockHttpServletRequest request = request("10.42.0.7");
        request.addHeader("CF-Connecting-IP", "::ffff:203.0.113.9");

        assertEquals("203.0.113.9", resolver.resolve(request));
    }
}
