package com.rizkysiregar.narrativ.model.request.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a text by its size in UTF-8 bytes instead of its characters. BCrypt cannot hash more than 72 bytes and
 * throws, and one character (an accent, an emoji) can take up to 4 bytes, so a character limit alone is not enough
 * to keep a password hashable.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "The text is too long, use fewer characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
