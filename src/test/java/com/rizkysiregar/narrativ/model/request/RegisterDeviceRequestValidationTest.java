package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterDeviceRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterDeviceRequest withPlatform(String platform) {
        RegisterDeviceRequest request = new RegisterDeviceRequest();
        request.setPushToken("token");
        request.setPlatform(platform);
        return request;
    }

    @Test
    void acceptsTheKnownPlatformsInAnyCase() {
        // the Android app sends ANDROID, the web client web
        for (String platform : new String[]{"ANDROID", "android", "WEB", "web", "IOS"}) {
            assertTrue(validator.validate(withPlatform(platform)).isEmpty(), platform);
        }
    }

    @Test
    void rejectsAnythingElse() {
        assertEquals(1, validator.validate(withPlatform("desktop")).size());
        assertEquals(1, validator.validate(withPlatform("web ")).size());
        // blank breaks both rules
        assertFalse(validator.validate(withPlatform("")).isEmpty());
    }
}
