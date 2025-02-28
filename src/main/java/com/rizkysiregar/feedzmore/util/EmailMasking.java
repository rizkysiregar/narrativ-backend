package com.rizkysiregar.feedzmore.util;

public class EmailMasking {
    public static String maskEmail(String email) {
        String[] parts = email.split("@");
        if (parts.length != 2) return email; // Invalid email format

        String username = parts[0];
        String domain = parts[1];

        // Show first letter, mask the rest except the last letter
        return username.charAt(0) + "****" + username.charAt(username.length() - 1) + "@" + domain;
    }
}
