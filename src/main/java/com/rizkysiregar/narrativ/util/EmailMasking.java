package com.rizkysiregar.narrativ.util;

public class EmailMasking {
    public static String maskEmail(String email) {
        if (email == null) return "";
        String[] parts = email.split("@");
        if (parts.length != 2) return "****"; // not an email, never echo it back

        String username = parts[0];
        String domain = parts[1];
        if (username.isEmpty()) return "****@" + domain;

        // Show first letter, mask the rest except the last letter
        return username.charAt(0) + "****" + username.charAt(username.length() - 1) + "@" + domain;
    }
}
