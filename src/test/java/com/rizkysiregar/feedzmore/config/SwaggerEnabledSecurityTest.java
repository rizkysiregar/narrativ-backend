package com.rizkysiregar.feedzmore.config;

import com.rizkysiregar.feedzmore.filter.AppCheckFilter;
import com.rizkysiregar.feedzmore.filter.JwtFilter;
import com.rizkysiregar.feedzmore.filter.RateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Development keeps working: the Swagger UI is loaded by a browser, which cannot send a Bearer token for the page. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=swagger-enabled-test",
                "app.cors.allowed-origins=http://localhost:3000",
                "springdoc.swagger-ui.enabled=true",
                "management.tracing.export.zipkin.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
        })
class SwaggerEnabledSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestApp {
    }

    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JsonAccessDeniedHandler accessDeniedHandler;
    @MockitoBean JwtFilter jwtFilter;
    @MockitoBean RateLimitFilter rateLimitFilter;
    @MockitoBean AppCheckFilter appCheckFilter;

    @Value("${local.server.port}") int port;

    @BeforeEach
    void passThroughFilters() throws Exception {
        for (Object filter : new Object[]{jwtFilter, rateLimitFilter, appCheckFilter}) {
            doAnswer(call -> {
                call.<FilterChain>getArgument(2).doFilter(call.getArgument(0), call.getArgument(1));
                return null;
            }).when((jakarta.servlet.Filter) filter).doFilter(any(), any(), any());
        }
    }

    @Test
    void swaggerPathsAreNotBehindLogin() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html")).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isNotIn(401, 403);
    }
}
