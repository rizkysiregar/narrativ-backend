package com.rizkysiregar.narrativ.util;

import java.util.Locale;

/**
 * One spelling per mailbox: emails are stored, looked up and used as Redis keys (OTP, reset and registration
 * tokens) trimmed and in lower case, so {@code Alice@Mail.com} and {@code alice@mail.com} can never be two accounts
 * or two OTP budgets.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
