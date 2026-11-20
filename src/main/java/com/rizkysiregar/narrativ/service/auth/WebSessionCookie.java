package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.service.AuthService;
import com.rizkysiregar.narrativ.util.ClientPlatform;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token of a web client lives in an HttpOnly cookie instead of the JSON body, so a script injected into
 * the page cannot read a 30 day session. Mobile clients are untouched: only a request with
 * {@code X-Client-Platform: web} gets or reads the cookie.
 * <p>
 * CSRF: the cookie is {@code SameSite=Strict} (the web app and the API share a site, other sites never send it), and
 * it is only read together with the custom header, which a cross-origin page cannot send without passing CORS.
 */
@Component
public class WebSessionCookie {

    public static final String NAME = "narrativ_refresh";

    private static final Duration MAX_AGE = Duration.ofSeconds(AuthService.REFRESH_TOKEN_TTL_SECONDS);

    private final boolean secure;

    // false only for a backend served over plain http to a browser that refuses Secure cookies there
    public WebSessionCookie(@Value("${app.auth.web-cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    /** For a web client, moves the refresh token of a new session into the cookie and out of the body. */
    public LoginResponse deliver(LoginResponse session, HttpServletRequest request, HttpServletResponse response) {
        if (!ClientPlatform.isWeb(request)) {
            return session;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, session.getRefreshToken(), MAX_AGE).toString());
        session.setRefreshToken(null);
        return session;
    }

    /** The refresh token sent in the body, or else the cookie of a web client; null when there is neither. */
    public String refreshToken(String bodyToken, HttpServletRequest request) {
        if (bodyToken != null && !bodyToken.isBlank()) {
            return bodyToken;
        }
        if (!ClientPlatform.isWeb(request) || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        if (ClientPlatform.isWeb(request)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
        }
    }

    private ResponseCookie cookie(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                // only /auth/refresh and /auth/logout need it, no other request carries the session
                .path(request.getContextPath() + "/auth")
                .maxAge(maxAge)
                .build();
    }
}
