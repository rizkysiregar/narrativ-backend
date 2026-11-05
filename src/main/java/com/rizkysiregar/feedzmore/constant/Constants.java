package com.rizkysiregar.feedzmore.constant;

import java.util.List;

public class Constants {
    public static final List<String> JWT_WHITELIST = List.of(
            "/auth/register",
            "/auth/admin/login",
            "/auth/login",
            "/auth/google",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/verify-otp",
            "/auth/reset-password",
            "/hello",
            "/res/photo",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/forgot-password",
            "/actuator/**"
    );

    public static final List<String> METHOD_LIST = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
    );

    public static final List<String> CORS_WHITELIST_ORIGIN = List.of(
            "http://localhost:53375",
            "http://localhost:8083",
            "http://localhost:3000", // web admin react
            "http://api-dev-feedzmore.rizkysiregar.my.id",
            "https://api-dev-feedzmore.rizkysiregar.my.id"
    );

    public static final String INVALID_USERNAME_PASSWORD = "Invalid Username or Password";

    // FIREBASE / NOTIFICATION
    public static final String FIREBASE_TOPIC_GLOBAL = "global";
    public static final String NOTIFICATION_NEW_FEED_TITLE = "Ada yang baru nih!";
    public static final String NOTIFICATION_NEW_FEED_BODY = "%s baru saja mengungah sesuatu. Yuk!,jadi yang pertama memberikan like!";
}
