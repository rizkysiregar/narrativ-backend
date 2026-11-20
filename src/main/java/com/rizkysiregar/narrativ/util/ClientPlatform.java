package com.rizkysiregar.narrativ.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Which client sent a request, as the client states it in {@code X-Client-Platform} (web, android) and
 * {@code X-App-Version}. Self-declared, so it only picks a response format (the web gets its refresh token as a
 * cookie) and labels the access log; it never grants anything.
 */
public final class ClientPlatform {

    public static final String HEADER = "X-Client-Platform";
    public static final String VERSION_HEADER = "X-App-Version";
    public static final String WEB = "web";

    // anything else would let a client write arbitrary text into the access log
    private static final String SAFE_VALUE = "[A-Za-z0-9._+-]{1,40}";

    private ClientPlatform() {
    }

    public static boolean isWeb(HttpServletRequest request) {
        return WEB.equalsIgnoreCase(request.getHeader(HEADER));
    }

    /** {@code platform/version} for the access log, {@code -} for what is missing or not a plain token. */
    public static String describe(HttpServletRequest request) {
        return safe(request.getHeader(HEADER)) + "/" + safe(request.getHeader(VERSION_HEADER));
    }

    private static String safe(String value) {
        return value != null && value.matches(SAFE_VALUE) ? value : "-";
    }
}
