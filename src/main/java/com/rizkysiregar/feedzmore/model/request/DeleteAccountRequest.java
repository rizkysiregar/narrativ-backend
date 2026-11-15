package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

/**
 * Proof that the caller owns the account: the current password, or (for an account that signs in with Google)
 * a fresh Google id token. One of the two is required, checked in the service.
 */
public record DeleteAccountRequest(
        @Size(max = 255, message = "{validation.password.tooLong}")
        String password,

        @Size(max = 4096, message = "{validation.token.tooLong}")
        String idToken
) {
    @Override
    public String toString() {
        return "DeleteAccountRequest[redacted]";
    }
}
