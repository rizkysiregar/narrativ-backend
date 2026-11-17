package com.rizkysiregar.narrativ.constant;

import java.time.ZoneId;
import java.util.List;

public class Constants {
    public static final List<String> JWT_WHITELIST = List.of(
            "/auth/register",
            "/auth/register/otp",
            "/auth/register/verify-otp",
            "/auth/admin/login",
            "/auth/login",
            "/auth/google",
            "/auth/refresh",
            "/auth/logout",
            "/auth/forgot-password",
            "/auth/verify-otp",
            "/auth/reset-password",
            "/res/photo",
            "/forgot-password",
            "/actuator/health/**",
            "/actuator/info"
    );

    // API documentation; SecurityConfig only opens these where springdoc is switched on (not in prod)
    // public pages for people without an account and for the Play Console; GET only, listed one by one (never a wildcard)
    public static final List<String> LEGAL_PAGES = List.of(
            "/legal/privacy-policy",
            "/legal/terms",
            "/legal/delete-account"
    );

    public static final List<String> SWAGGER_WHITELIST = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    public static final List<String> METHOD_LIST = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    // time zone used for admin calendar boundaries (audit log date filters, dashboard statistics)
    public static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Jakarta");

    public static final String INVALID_USERNAME_PASSWORD = "Invalid Username or Password";

    // FIREBASE / NOTIFICATION
    public static final String FIREBASE_TOPIC_GLOBAL = "global";
    public static final String NOTIFICATION_POST_TAKEDOWN_TITLE = "Postingan Anda diturunkan";
    public static final String NOTIFICATION_POST_TAKEDOWN_BODY = "Postingan Anda diturunkan oleh admin. Alasan: %s";
    public static final String NOTIFICATION_COMMENT_TAKEDOWN_TITLE = "Komentar Anda diturunkan";
    public static final String NOTIFICATION_COMMENT_TAKEDOWN_BODY = "Komentar Anda diturunkan oleh admin. Alasan: %s";
    public static final String NOTIFICATION_TYPE_POST_LIKED = "POST_LIKED";
    public static final String NOTIFICATION_TYPE_POST_COMMENTED = "POST_COMMENTED";
    public static final String NOTIFICATION_TYPE_COMMENT_REPLIED = "COMMENT_REPLIED";
    public static final String NOTIFICATION_TYPE_NEW_POST = "NEW_POST";
}
