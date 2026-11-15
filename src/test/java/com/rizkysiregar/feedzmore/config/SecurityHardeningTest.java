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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Production profile: the API documentation is switched off, and the only way to log in is a JWT
 * (HTTP Basic would let anybody guess the admin password without the rate limit of /auth/**).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // do not load application.yml: it needs the deployment env vars, which CI does not have
                "spring.config.name=security-hardening-test",
                "app.cors.allowed-origins=http://localhost:3000",
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false",
                "management.tracing.export.zipkin.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
        })
class SecurityHardeningTest {

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

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void passThroughFilters() throws Exception {
        for (Object filter : new Object[]{jwtFilter, rateLimitFilter, appCheckFilter}) {
            doAnswer(call -> {
                call.<FilterChain>getArgument(2).doFilter(call.getArgument(0), call.getArgument(1));
                return null;
            }).when((jakarta.servlet.Filter) filter).doFilter(any(), any(), any());
        }
    }

    private HttpResponse<Void> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void basicAuthIsNotAcceptedAndNotOffered() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString("admin:guess".getBytes());

        HttpResponse<Void> response = get("/posts", "Authorization", basic);

        assertThat(response.statusCode()).isEqualTo(401);
        // no "WWW-Authenticate: Basic" challenge either: the app does not speak Basic
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        verifyUserDetailsServiceNeverAskedAboutBasicUser();
    }

    private void verifyUserDetailsServiceNeverAskedAboutBasicUser() {
        org.mockito.Mockito.verifyNoInteractions(userDetailsService);
    }

    @Test
    void missingLoginStaysUnauthorizedNotForbidden() throws Exception {
        assertThat(get("/posts").statusCode()).isEqualTo(401);
    }

    @Test
    void swaggerAndApiDocsAreClosedWhenSpringdocIsOff() throws Exception {
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(401);
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(401);
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(401);
    }

    @Test
    void helloEndpointIsGone() throws Exception {
        assertThat(get("/hello/version").statusCode()).isEqualTo(401);
    }
}
