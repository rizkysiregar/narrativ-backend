package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.request.RefreshTokenRequest;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.service.AuthService;
import com.rizkysiregar.narrativ.service.auth.WebSessionCookie;
import com.rizkysiregar.narrativ.service.otp.IOtpService;
import com.rizkysiregar.narrativ.service.otp.IRegistrationOtpService;
import com.rizkysiregar.narrativ.util.ClientPlatform;
import com.rizkysiregar.narrativ.util.Operation;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The refresh and logout endpoints for a web client (cookie) next to a mobile client (body). */
class AuthControllerWebSessionTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService, mock(IOtpService.class),
            mock(IRegistrationOtpService.class), new WebSessionCookie(true));

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest webRequest(String cookieValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api/v1");
        request.addHeader(ClientPlatform.HEADER, "web");
        if (cookieValue != null) {
            request.setCookies(new Cookie(WebSessionCookie.NAME, cookieValue));
        }
        return request;
    }

    private static RefreshTokenRequest body(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        return request;
    }

    private static LoginResponse rotated() {
        return LoginResponse.builder().token("access-2").refreshToken("refresh-2").role(Role.USER).build();
    }

    @Test
    void refresh_Web_UsesTheCookieAndRotatesIt() {
        when(authService.refreshToken("refresh-1")).thenReturn(rotated());

        LoginResponse answer = controller.refresh(null, webRequest("refresh-1"), response);

        assertThat(answer.getToken()).isEqualTo("access-2");
        assertThat(answer.getRefreshToken()).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(WebSessionCookie.NAME + "=refresh-2");
    }

    @Test
    void refresh_Mobile_StaysInTheBody() {
        when(authService.refreshToken("refresh-1")).thenReturn(rotated());

        LoginResponse answer = controller.refresh(body("refresh-1"), new MockHttpServletRequest(), response);

        assertThat(answer.getRefreshToken()).isEqualTo("refresh-2");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void refresh_NoTokenAnywhere_IsUnauthorized() {
        assertThatThrownBy(() -> controller.refresh(null, webRequest(null), response))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.refresh(body(""), new MockHttpServletRequest(), response))
                .isInstanceOf(BusinessException.class);
        verify(authService, never()).refreshToken(any());
    }

    @Test
    void logout_Web_RevokesTheCookieTokenAndClearsIt() {
        Operation answer = controller.logout(null, webRequest("refresh-1"), response);

        assertThat(answer).isEqualTo(Operation.DELETED);
        verify(authService).logout("refresh-1");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void logout_WithoutAnyToken_StillSucceeds() {
        assertThat(controller.logout(null, webRequest(null), response)).isEqualTo(Operation.DELETED);
        verify(authService, never()).logout(any());
    }
}
