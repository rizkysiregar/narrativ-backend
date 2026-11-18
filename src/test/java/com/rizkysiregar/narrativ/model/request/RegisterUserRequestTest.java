package com.rizkysiregar.narrativ.model.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterUserRequestTest {

    // same as the application's ObjectMapper: it fails on unknown properties
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registersWithoutAPhoneNumber() throws Exception {
        RegisterUserRequest request = objectMapper.readValue("""
                {"username":"rizky","password":"secret123","email":"rizky@example.com","firstName":"Rizky","lastName":"Siregar"}
                """, RegisterUserRequest.class);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void appsThatStillSendAPhoneNumberAreNotRejected() throws Exception {
        RegisterUserRequest request = objectMapper.readValue("""
                {"username":"rizky","password":"secret123","email":"rizky@example.com","firstName":"Rizky","lastName":"Siregar","phoneNumber":"081234567890"}
                """, RegisterUserRequest.class);

        assertEquals("rizky", request.getUsername());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void aProfileImageSentAtRegistrationIsIgnored() throws Exception {
        // it used to be stored as is, and an object key there was presigned for anybody: any object of the bucket
        RegisterUserRequest request = objectMapper.readValue("""
                {"username":"rizky","password":"secret123","email":"rizky@example.com","firstName":"Rizky","lastName":"Siregar","profileImage":"victim/POST_FEED/a.jpg"}
                """, RegisterUserRequest.class);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void usernameIsLimitedToLettersDigitsDotsAndUnderscores() {
        for (String ok : new String[]{"rizky", "Rizky_99", "r.siregar"}) {
            assertTrue(validator.validateProperty(withUsername(ok), "username").isEmpty(), ok);
        }
        for (String bad : new String[]{"a/b/c", "has space", "rízky", "rizky!", "../x"}) {
            assertFalse(validator.validateProperty(withUsername(bad), "username").isEmpty(), bad);
        }
    }

    private static RegisterUserRequest withUsername(String username) {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername(username);
        return request;
    }
}
