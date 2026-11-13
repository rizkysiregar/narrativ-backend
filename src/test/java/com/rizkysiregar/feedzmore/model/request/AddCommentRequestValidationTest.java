package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddCommentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AddCommentRequest withContent(String content) {
        AddCommentRequest request = new AddCommentRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void acceptsATextUpToTheColumnLength() {
        assertTrue(validator.validate(withContent("halo")).isEmpty());
        assertTrue(validator.validate(withContent("a".repeat(255))).isEmpty());
    }

    @Test
    void rejectsAMissingEmptyOrBlankText() {
        assertEquals(1, validator.validate(withContent(null)).size());
        assertEquals(1, validator.validate(withContent("")).size());
        assertEquals(1, validator.validate(withContent("   ")).size());
    }

    @Test
    void rejectsATextLongerThanTheColumn() {
        // comments.content is VARCHAR(255): longer would be a database error
        assertEquals(1, validator.validate(withContent("a".repeat(256))).size());
    }
}
