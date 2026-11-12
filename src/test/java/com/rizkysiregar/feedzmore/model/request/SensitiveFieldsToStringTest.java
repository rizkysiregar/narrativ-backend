package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** The requests and events get logged now and then; their toString must never carry a secret. */
class SensitiveFieldsToStringTest {

    @Test
    void loginRequestHidesThePassword() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUsername("rizky");
        request.setPassword("s3cret-password");

        assertFalse(request.toString().contains("s3cret-password"));
    }

    @Test
    void registerRequestHidesPasswordAndEmail() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("rizky");
        request.setPassword("s3cret-password");
        request.setEmail("rizky@example.com");

        String text = request.toString();
        assertFalse(text.contains("s3cret-password"));
        assertFalse(text.contains("rizky@example.com"));
    }

    @Test
    void resetPasswordRequestHidesTokenAndPassword() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("rizky@example.com")
                .resetToken("reset-token-value")
                .newPassword("s3cret-password")
                .build();

        String text = request.toString();
        assertFalse(text.contains("reset-token-value"));
        assertFalse(text.contains("s3cret-password"));
        assertFalse(text.contains("rizky@example.com"));
    }

    @Test
    void otpRefreshTokenAndGoogleTokenAreHidden() {
        VerifyOtpRequest otp = VerifyOtpRequest.builder().email("a@b.co").otp("123456").build();
        RefreshTokenRequest refresh = new RefreshTokenRequest();
        refresh.setRefreshToken("refresh-token-value");
        GoogleLoginRequest google = new GoogleLoginRequest();
        google.setIdToken("google-id-token-value");

        assertFalse(otp.toString().contains("123456"));
        assertFalse(refresh.toString().contains("refresh-token-value"));
        assertFalse(google.toString().contains("google-id-token-value"));
    }

    @Test
    void postCreatedEventHidesTheAuthorEmail() {
        PostCreatedEvent event = PostCreatedEvent.builder().email("author@example.com").build();

        assertFalse(event.toString().contains("author@example.com"));
    }
}
