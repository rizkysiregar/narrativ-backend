package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetPasswordRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ResetPasswordRequest withPassword(String password) {
        return ResetPasswordRequest.builder()
                .email("user@example.com")
                .resetToken("token")
                .newPassword(password)
                .build();
    }

    @Test
    void acceptsAPasswordWithinTheLimits() {
        assertTrue(validator.validate(withPassword("abcdef")).isEmpty());
        assertTrue(validator.validate(withPassword("a".repeat(64))).isEmpty());
    }

    @Test
    void rejectsATooShortPassword() {
        assertEquals(1, validator.validate(withPassword("abc")).size());
    }

    @Test
    void rejectsATooLongPassword() {
        assertEquals(1, validator.validate(withPassword("a".repeat(65))).size());
    }
}
