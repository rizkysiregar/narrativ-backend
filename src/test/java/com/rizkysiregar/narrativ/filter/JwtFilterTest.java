package com.rizkysiregar.narrativ.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.service.JWTService;
import com.rizkysiregar.narrativ.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JWTService jwtService;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtFilter filter() {
        return new JwtFilter(jwtService, userDetailsService, objectMapper);
    }

    private UserPrincipal principal(UserStatus status, boolean deleted) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("someone");
        user.setRole(Role.USER);
        user.setStatus(status);
        user.setDeleted(deleted);
        user.setBanReason(status == UserStatus.BANNED ? "posting spam" : null);
        return new UserPrincipal(user);
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        return request;
    }

    @Test
    void legalPagesIgnoreAnAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legal/privacy-policy");
        request.addHeader("Authorization", "Bearer stale.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertEquals(200, response.getStatus());
    }

    @Test
    void bannedUser_gets401UserBannedAndTheRequestNeverReachesTheController() throws Exception {
        UserPrincipal banned = principal(UserStatus.BANNED, false);
        when(jwtService.extractUserId("some.jwt.token")).thenReturn(banned.getUserId());
        when(userDetailsService.loadUserById(banned.getUserId())).thenReturn(banned);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(authorizedRequest(), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Your account has been banned by an admin. Reason: posting spam"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(filterChain);
    }

    @Test
    void deletedUser_stillGetsTheDisabledMessage() throws Exception {
        UserPrincipal deleted = principal(UserStatus.ACTIVE, true);
        when(jwtService.extractUserId("some.jwt.token")).thenReturn(deleted.getUserId());
        when(userDetailsService.loadUserById(deleted.getUserId())).thenReturn(deleted);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(authorizedRequest(), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(GlobalMessage.USER_DISABLED.getMessage()));
        verifyNoInteractions(filterChain);
    }

    @Test
    void activeUser_isAuthenticatedAndPassesThrough() throws Exception {
        UserPrincipal active = principal(UserStatus.ACTIVE, false);
        when(jwtService.extractUserId("some.jwt.token")).thenReturn(active.getUserId());
        when(userDetailsService.loadUserById(active.getUserId())).thenReturn(active);
        when(jwtService.validateToken(any(), any())).thenReturn(true);
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tokenWhoseSubjectIsNotAUserId_isRejected() throws Exception {
        // tokens issued before the subject became the user id carry the username: they must answer 401 so the app
        // refreshes, never resolve to whoever holds that username now
        when(jwtService.extractUserId("some.jwt.token")).thenThrow(new IllegalArgumentException("Invalid UUID string: someone"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(authorizedRequest(), response, filterChain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain, userDetailsService);
    }

    private MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = authorizedRequest();
        request.setContextPath("/api/v1");
        request.setRequestURI("/api/v1" + path);
        return request;
    }

    @Test
    void publicAuthEndpoints_ignoreAStaleAuthorizationHeader() throws Exception {
        for (String path : new String[]{"/auth/login", "/auth/admin/login", "/auth/google", "/auth/refresh", "/auth/register"}) {
            MockHttpServletRequest request = requestTo(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter().doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus(), path);
            verify(filterChain).doFilter(request, response);
            org.mockito.Mockito.clearInvocations(filterChain);
        }
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void registerAdmin_stillNeedsAValidToken() throws Exception {
        when(jwtService.extractUserId("some.jwt.token"))
                .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(requestTo("/auth/register-admin"), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(GlobalMessage.UNAUTHORIZED.getMessage()));
        verifyNoInteractions(filterChain);
    }

    @Test
    void otherEndpoints_stillRejectAnInvalidToken() throws Exception {
        when(jwtService.extractUserId("some.jwt.token"))
                .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(requestTo("/users/me"), response, filterChain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }
}
