package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.util.ClientPlatform;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WebSessionCookieTest {

    private final WebSessionCookie cookie = new WebSessionCookie(true);

    private MockHttpServletRequest request(boolean web) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api/v1");
        if (web) {
            request.addHeader(ClientPlatform.HEADER, "web");
        }
        return request;
    }

    private LoginResponse session() {
        return LoginResponse.builder().token("access").refreshToken("refresh-1").username("ani").userId("u1")
                .role(Role.USER).build();
    }

    @Test
    void deliver_Web_MovesRefreshTokenIntoAHardenedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        LoginResponse delivered = cookie.deliver(session(), request(true), response);

        assertThat(delivered.getRefreshToken()).isNull();
        assertThat(delivered.getToken()).isEqualTo("access");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(WebSessionCookie.NAME + "=refresh-1")
                .contains("Path=/api/v1/auth", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Strict");
    }

    @Test
    void deliver_Mobile_KeepsTheBodyAndSetsNoCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        LoginResponse delivered = cookie.deliver(session(), request(false), response);

        assertThat(delivered.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void deliver_InsecureMode_LeavesOutSecure() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new WebSessionCookie(false).deliver(session(), request(true), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure").contains("HttpOnly");
    }

    @Test
    void refreshToken_PrefersTheBody() {
        MockHttpServletRequest request = request(true);
        request.setCookies(new Cookie(WebSessionCookie.NAME, "from-cookie"));

        assertThat(cookie.refreshToken("from-body", request)).isEqualTo("from-body");
    }

    @Test
    void refreshToken_Web_FallsBackToTheCookie() {
        MockHttpServletRequest request = request(true);
        request.setCookies(new Cookie("other", "x"), new Cookie(WebSessionCookie.NAME, "from-cookie"));

        assertThat(cookie.refreshToken(null, request)).isEqualTo("from-cookie");
        assertThat(cookie.refreshToken("  ", request)).isEqualTo("from-cookie");
    }

    @Test
    void refreshToken_CookieIgnoredWithoutTheWebHeader() {
        // the custom header is the CSRF guard: a plain cross-site form post cannot send it
        MockHttpServletRequest request = request(false);
        request.setCookies(new Cookie(WebSessionCookie.NAME, "from-cookie"));

        assertThat(cookie.refreshToken(null, request)).isNull();
    }

    @Test
    void refreshToken_NullWhenThereIsNone() {
        assertThat(cookie.refreshToken(null, request(true))).isNull();
    }

    @Test
    void clear_Web_ExpiresTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookie.clear(request(true), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(WebSessionCookie.NAME + "=;")
                .contains("Max-Age=0", "Path=/api/v1/auth");
    }

    @Test
    void clear_Mobile_SetsNoCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookie.clear(request(false), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
