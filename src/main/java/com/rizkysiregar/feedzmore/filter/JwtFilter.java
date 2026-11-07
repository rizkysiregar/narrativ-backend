package com.rizkysiregar.feedzmore.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;
import com.rizkysiregar.feedzmore.service.JWTService;
import com.rizkysiregar.feedzmore.util.BanMessages;
import com.rizkysiregar.feedzmore.service.impl.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JWTService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;

    /**
     * The public auth endpoints (login, refresh, google, register, otp, ...) decide for themselves, so an
     * Authorization header that a client still holds (an expired token from an earlier session, one pasted
     * into Swagger) must not answer for them: it would hide the real result, e.g. that the account is banned.
     * Only register-admin needs the caller to be authenticated.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return path.startsWith("/auth/") && !path.startsWith("/auth/register-admin");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                validateUserIsEnabled(userDetails);

                if (userDetails instanceof UserPrincipal principal && principal.isBanned()) {
                    log.warn("Rejected JWT request because user is banned: {}", username);
                    writeUnauthorizedResponse(response, GlobalMessage.USER_BANNED.getCode(),
                            BanMessages.bannedMessage(principal.getBanReason()));
                    return;
                }

                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);
        } catch (DisabledException exception) {
            log.warn("Rejected JWT request because user is disabled: {}", exception.getMessage());
            writeUnauthorizedResponse(response, GlobalMessage.USER_DISABLED);
        } catch (UsernameNotFoundException exception) {
            log.warn("Rejected JWT request because user was not found: {}", exception.getMessage());
            writeUnauthorizedResponse(response, GlobalMessage.BAD_CREDENTIALS);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Rejected JWT request because token is invalid: {}", exception.getMessage());
            writeUnauthorizedResponse(response, GlobalMessage.UNAUTHORIZED);
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        return authHeader.substring(7);
    }

    private void validateUserIsEnabled(UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, GlobalMessage message) throws IOException {
        writeUnauthorizedResponse(response, message.getCode(), message.getMessage());
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        BaseResponse<?> body = BaseResponse.builder()
                .code(code)
                .message(message)
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}
