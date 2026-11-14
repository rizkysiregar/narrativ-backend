package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

/**
 * Proof that the caller owns the account: the current password, or (for an account that signs in with Google)
 * a fresh Google id token. One of the two is required, checked in the service.
 */
public record DeleteAccountRequest(
        @Size(max = 255, message = "Password is too long")
        String password,

        @Size(max = 4096, message = "Token is too long")
        String idToken
) {
    @Override
    public String toString() {
        return "DeleteAccountRequest[redacted]";
    }
}
