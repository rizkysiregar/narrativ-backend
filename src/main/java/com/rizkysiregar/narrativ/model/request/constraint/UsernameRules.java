package com.rizkysiregar.narrativ.model.request.constraint;

/**
 * What a username may look like. It shows up in URLs ({@code /posts/users/{username}/posts}) and in mentions, so only
 * letters, digits, dots and underscores: no slash, no spaces, no look-alike unicode characters.
 */
public final class UsernameRules {

    public static final String PATTERN = "^[A-Za-z0-9._]+$";

    private UsernameRules() {
    }
}
