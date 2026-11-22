package com.rizkysiregar.narrativ.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    /**
     * The server is the context path alone, without scheme and host: Swagger UI then calls the API on the origin of
     * the page it is opened from. A generated absolute URL would be http:// behind Cloudflare Tunnel and ingress-nginx
     * (the backend does not see the original scheme), and a browser blocks "Try it out" from the https:// page as
     * mixed content, which Swagger reports as a CORS failure.
     */
    @Bean
    public OpenAPI customOpenApi(@Value("${server.servlet.context-path:/}") String contextPath) {
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl(contextPath))))
                .info(new Info().title("NARRATIV API").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSchemas(ResponseEnvelopeCustomizer.ERROR_SCHEMA, ResponseEnvelopeCustomizer.errorSchema())
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    static String serverUrl(String contextPath) {
        String path = contextPath == null ? "" : contextPath.trim().replaceAll("/+$", "");
        return path.isEmpty() ? "/" : (path.startsWith("/") ? path : "/" + path);
    }
}
