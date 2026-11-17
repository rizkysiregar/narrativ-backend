package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.GoogleUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleTokenVerifierServiceTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";

    private GoogleTokenVerifierService service;

    @BeforeEach
    void setup() {
        service = new GoogleTokenVerifierService();
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
    }

    /** What https://oauth2.googleapis.com/tokeninfo answers: the booleans come as strings. */
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
}
