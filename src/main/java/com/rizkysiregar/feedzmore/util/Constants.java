package com.rizkysiregar.feedzmore.util;

import java.util.List;

public class Constants {
    public static final List<String> JWT_WHITELIST = List.of(
            "/register",
            "/login",
            "/res/photo",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );
}

