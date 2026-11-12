package com.rizkysiregar.feedzmore.model.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterUserRequestTest {

    // same as the application's ObjectMapper: it fails on unknown properties
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registersWithoutAPhoneNumber() throws Exception {
        RegisterUserRequest request = objectMapper.readValue("""
                {"username":"rizky","password":"secret1","email":"rizky@example.com","firstName":"Rizky","lastName":"Siregar"}
                """, RegisterUserRequest.class);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void appsThatStillSendAPhoneNumberAreNotRejected() throws Exception {
        RegisterUserRequest request = objectMapper.readValue("""
                {"username":"rizky","password":"secret1","email":"rizky@example.com","firstName":"Rizky","lastName":"Siregar","phoneNumber":"081234567890"}
                """, RegisterUserRequest.class);

        assertEquals("rizky", request.getUsername());
        assertTrue(validator.validate(request).isEmpty());
    }
}
