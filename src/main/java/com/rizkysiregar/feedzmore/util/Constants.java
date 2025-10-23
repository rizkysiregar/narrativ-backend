package com.rizkysiregar.feedzmore.util;

import java.util.List;

public class Constants {
    public static final List<String> JWT_WHITELIST = List.of(
            "/register",
            "/login",
            "/hello",
            "/res/photo",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/forgot-password",
            "/verify-otp",
            "/reset-password"
    );

    public static final List<String> METHOD_LIST = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
    );

    public static final List<String> CORS_WHITELIST_ORIGIN = List.of(
            "http://localhost:53375",
            "http://localhost:8083"
    );

    public static final String INVALID_USERNAME_PASSWORD = "Invalid Username or Password";
}

