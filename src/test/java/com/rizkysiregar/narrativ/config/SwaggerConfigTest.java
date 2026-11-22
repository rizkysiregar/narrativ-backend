package com.rizkysiregar.narrativ.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void server_IsTheContextPathOnly_SoSwaggerUsesTheOriginOfThePage() {
        OpenAPI api = new SwaggerConfig().customOpenApi("/api/v1");

        assertThat(api.getServers()).hasSize(1);
        assertThat(api.getServers().getFirst().getUrl()).isEqualTo("/api/v1");
    }

    @Test
    void serverUrl_NormalisesTheContextPath() {
        assertThat(SwaggerConfig.serverUrl("/api/v1/")).isEqualTo("/api/v1");
        assertThat(SwaggerConfig.serverUrl("api/v1")).isEqualTo("/api/v1");
        assertThat(SwaggerConfig.serverUrl("/")).isEqualTo("/");
        assertThat(SwaggerConfig.serverUrl("")).isEqualTo("/");
        assertThat(SwaggerConfig.serverUrl(null)).isEqualTo("/");
    }
}
