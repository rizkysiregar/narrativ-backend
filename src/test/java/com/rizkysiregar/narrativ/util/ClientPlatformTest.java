package com.rizkysiregar.narrativ.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientPlatformTest {

    private MockHttpServletRequest request(String platform, String version) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (platform != null) {
            request.addHeader(ClientPlatform.HEADER, platform);
        }
        if (version != null) {
            request.addHeader(ClientPlatform.VERSION_HEADER, version);
        }
        return request;
    }

    @Test
    void isWeb_OnlyForTheWebPlatform_IgnoringCase() {
        assertThat(ClientPlatform.isWeb(request("web", null))).isTrue();
        assertThat(ClientPlatform.isWeb(request("WEB", null))).isTrue();
        assertThat(ClientPlatform.isWeb(request("android", null))).isFalse();
        assertThat(ClientPlatform.isWeb(request(null, null))).isFalse();
    }

    @Test
    void describe_PlatformAndVersion() {
        assertThat(ClientPlatform.describe(request("web", "1.4.0+build.7"))).isEqualTo("web/1.4.0+build.7");
    }

    @Test
    void describe_DashForMissingOrUnsafeValues() {
        assertThat(ClientPlatform.describe(request(null, null))).isEqualTo("-/-");
        assertThat(ClientPlatform.describe(request("web userId=admin", "1.0\nHTTP.REQUEST"))).isEqualTo("-/-");
        assertThat(ClientPlatform.describe(request("x".repeat(41), "1.0"))).isEqualTo("-/1.0");
    }
}
