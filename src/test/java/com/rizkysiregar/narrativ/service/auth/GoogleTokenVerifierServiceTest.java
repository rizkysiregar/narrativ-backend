package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.GoogleUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenVerifierServiceTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";

    private GoogleTokenVerifierService service;
    private final JwtDecoder decoder = mock(JwtDecoder.class);

    @BeforeEach
    void setup() {
        service = new GoogleTokenVerifierService(decoder);
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
    }

    /** The claims of an ID token (the older tokeninfo endpoint answered the booleans as strings, both are accepted). */
    private Map<String, Object> payload(String emailVerified) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", CLIENT_ID);
        payload.put("email", "rizky@example.com");
        payload.put("given_name", "Rizky");
        payload.put("family_name", "Siregar");
        payload.put("sub", "google-sub-1");
        if (emailVerified != null) {
            payload.put("email_verified", emailVerified);
        }
        return payload;
    }

    @Test
    void acceptsAVerifiedEmail() {
        GoogleUserInfo info = service.toVerifiedUser(payload("true"));

        assertEquals("rizky@example.com", info.getEmail());
        assertEquals("google-sub-1", info.getGoogleId());
    }

    @Test
    void rejectsAnEmailGoogleDidNotVerify() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.toVerifiedUser(payload("false")));

        assertEquals(GlobalMessage.GOOGLE_EMAIL_NOT_VERIFIED.getMessage(), error.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
    }

    @Test
    void rejectsATokenWithoutTheVerifiedFlag() {
        assertThrows(BusinessException.class, () -> service.toVerifiedUser(payload(null)));
    }

    @Test
    void rejectsATokenWithoutAnEmail() {
        Map<String, Object> noEmail = payload("true");
        noEmail.remove("email");

        BusinessException error = assertThrows(BusinessException.class, () -> service.toVerifiedUser(noEmail));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getHttpStatus());
    }

    @Test
    void rejectsATokenIssuedForAnotherClient() {
        Map<String, Object> other = payload("true");
        other.put("aud", "someone-else");

        BusinessException error = assertThrows(BusinessException.class, () -> service.toVerifiedUser(other));

        assertEquals(GlobalMessage.GOOGLE_TOKEN_MISS_MATCH.getMessage(), error.getMessage());
    }

    @Test
    void acceptsTheClaimsOfADecodedToken() {
        // a decoded JWT has the audience as a list and email_verified as a boolean
        Jwt jwt = Jwt.withTokenValue("id-token").header("alg", "RS256")
                .audience(List.of(CLIENT_ID)).subject("google-sub-1")
                .claim("email", "Rizky@Example.com").claim("email_verified", true)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(decoder.decode("id-token")).thenReturn(jwt);

        GoogleUserInfo info = service.verifyToken("id-token");

        assertEquals("rizky@example.com", info.getEmail());
        assertEquals("google-sub-1", info.getGoogleId());
    }

    @Test
    void aBadTokenIsUnauthorized() {
        when(decoder.decode("forged")).thenThrow(new BadJwtException("signature does not match"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.verifyToken("forged"));

        assertEquals(GlobalMessage.GOOGLE_UNAUTHORIZED.getMessage(), error.getMessage());
    }

    @Test
    void onlyGoogleIsAcceptedAsIssuer() {
        Jwt foreign = Jwt.withTokenValue("t").header("alg", "RS256").issuer("https://evil.example.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        Jwt google = Jwt.withTokenValue("t").header("alg", "RS256").issuer("https://accounts.google.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertTrue(GoogleTokenVerifierService.validators().validate(foreign).hasErrors());
        assertFalse(GoogleTokenVerifierService.validators().validate(google).hasErrors());
    }
}
