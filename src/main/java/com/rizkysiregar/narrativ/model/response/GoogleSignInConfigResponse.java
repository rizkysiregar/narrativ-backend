package com.rizkysiregar.narrativ.model.response;

/**
 * What the web client needs to show "Sign in with Google" in this environment: the OAuth client id whose ID tokens
 * POST /auth/google accepts. Public, not a secret. {@code enabled} is false while the environment has none.
 */
public record GoogleSignInConfigResponse(boolean enabled, String clientId) {
}
