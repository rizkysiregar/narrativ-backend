package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterUserRequest register(String password) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("rizky");
        request.setPassword(password);
        request.setEmail("rizky@example.com");
        request.setFirstName("Rizky");
        request.setLastName("Siregar");
        return request;
    }

    private ResetPasswordRequest reset(String password) {
        return ResetPasswordRequest.builder().email("rizky@example.com").resetToken("t").newPassword(password).build();
    }

    @Test
    void registerAcceptsEightToSixtyFourCharacters() {
        assertTrue(validator.validate(register("abcdefgh")).isEmpty());
        assertTrue(validator.validate(register("a".repeat(64))).isEmpty());
    }

    @Test
    void registerRejectsLessThanEightOrMoreThanSixtyFour() {
        assertEquals(1, validator.validate(register("abcdefg")).size());
        assertEquals(1, validator.validate(register("a".repeat(65))).size());
    }

    @Test
    void resetFollowsTheSameLimits() {
        assertTrue(validator.validate(reset("abcdefgh")).isEmpty());
        assertEquals(1, validator.validate(reset("abcdefg")).size());
        assertEquals(1, validator.validate(reset("a".repeat(65))).size());
    }

    @Test
    void aPasswordBCryptCannotHashIsRejectedInsteadOfFailingWith500() {
        // 40 characters, but 80 bytes: BCrypt refuses more than 72 bytes
        String multibyte = "\u00e9".repeat(40);
        assertEquals(1, validator.validate(register(multibyte)).size());
        assertEquals(1, validator.validate(reset(multibyte)).size());
        // what passes validation can always be hashed
        String longestAccepted = "\u00e9".repeat(36);
        assertTrue(validator.validate(register(longestAccepted)).isEmpty());
        assertDoesNotThrow(() -> new BCryptPasswordEncoder(4).encode(longestAccepted));
    }

    @Test
    void loginStillAcceptsShortPasswordsOfAccountsCreatedUnderTheOldRule() {
        // accounts registered with 6 or 7 characters must keep working: login has no length rule
        UserLoginRequest login = new UserLoginRequest();
        login.setUsername("rizky");
        login.setPassword("abc123");

        assertTrue(validator.validate(login).isEmpty());
    }
}
