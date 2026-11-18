package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** The new address an email change OTP is sent to. */
public record ChangeEmailOtpRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email
) {
}
