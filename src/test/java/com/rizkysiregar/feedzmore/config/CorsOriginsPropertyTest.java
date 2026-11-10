package com.rizkysiregar.feedzmore.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsOriginsPropertyTest {

    @Test
    void configuredOriginsAreTrimmedAndAppliedToCorsConfig() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "corsAllowedOrigins",
                new String[]{"https://a.example.com", " https://b.example.com ", ""});

        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfigurations().get("/**");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://a.example.com", "https://b.example.com");
    }
}
