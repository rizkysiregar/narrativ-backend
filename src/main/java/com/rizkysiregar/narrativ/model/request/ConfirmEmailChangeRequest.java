package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Completes an email change: the new address, the OTP that was sent to it, and proof that the caller owns the
 * account (the current password, or a fresh Google id token for an account that signs in with Google).
 */
public record ConfirmEmailChangeRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank
        @Size(min = 6, max = 6, message = "{validation.otp.digits}")
        String otp,

        @Size(max = 255, message = "{validation.password.tooLong}")
        String password,

        @Size(max = 4096, message = "{validation.token.tooLong}")
        String idToken
) {
    @Override
    public String toString() {
        return "ConfirmEmailChangeRequest[redacted]";
    }
}
