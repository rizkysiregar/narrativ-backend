package com.rizkysiregar.feedzmore.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.service.JWTService;
import com.rizkysiregar.feedzmore.service.impl.UserDetailsServiceImpl;
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
    void bannedUser_gets401UserBannedAndTheRequestNeverReachesTheController() throws Exception {
        UserPrincipal banned = principal(UserStatus.BANNED, false);
        when(jwtService.extractUsername("some.jwt.token")).thenReturn("someone");
        when(userDetailsService.loadUserByUsername("someone")).thenReturn(banned);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(authorizedRequest(), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Akun Anda telah diblokir oleh admin. Alasan: posting spam"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(filterChain);
    }

    @Test
    void deletedUser_stillGetsTheDisabledMessage() throws Exception {
        UserPrincipal deleted = principal(UserStatus.ACTIVE, true);
        when(jwtService.extractUsername("some.jwt.token")).thenReturn("someone");
        when(userDetailsService.loadUserByUsername("someone")).thenReturn(deleted);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(authorizedRequest(), response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(GlobalMessage.USER_DISABLED.getMessage()));
        verifyNoInteractions(filterChain);
    }

    @Test
    void activeUser_isAuthenticatedAndPassesThrough() throws Exception {
        UserPrincipal active = principal(UserStatus.ACTIVE, false);
        when(jwtService.extractUsername("some.jwt.token")).thenReturn("someone");
        when(userDetailsService.loadUserByUsername("someone")).thenReturn(active);
        when(jwtService.validateToken(any(), any())).thenReturn(true);
        MockHttpServletRequest request = authorizedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
