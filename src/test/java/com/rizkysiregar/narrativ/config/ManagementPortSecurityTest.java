package com.rizkysiregar.narrativ.config;

import com.rizkysiregar.narrativ.filter.AppCheckFilter;
import com.rizkysiregar.narrativ.filter.JwtFilter;
import com.rizkysiregar.narrativ.filter.RateLimitFilter;
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

/**
 * With a dedicated management port the probes and Prometheus are open there,
 * while the application port serves no actuator endpoint at all.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // do not load application.yml: it needs the deployment env vars, which CI does not have
                "spring.config.name=management-port-test",
                "app.cors.allowed-origins=http://localhost:3000",
                "management.server.port=19092",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.health.probes.enabled=true",
                "management.tracing.export.zipkin.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
        })
class ManagementPortSecurityTest {

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

    @Value("${local.server.port}") int appPort;

    private final HttpClient client = HttpClient.newHttpClient();

    private int status(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

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
    void managementPortServesPrometheusAndProbesWithoutLogin() throws Exception {
        assertThat(status("http://localhost:19092/actuator/prometheus")).isEqualTo(200);
        assertThat(status("http://localhost:19092/actuator/health/liveness")).isEqualTo(200);
    }

    @Test
    void managementPortStillProtectsOtherEndpoints() throws Exception {
        assertThat(status("http://localhost:19092/actuator/metrics")).isEqualTo(401);
    }

    @Test
    void applicationPortDoesNotServePrometheus() throws Exception {
        assertThat(status("http://localhost:" + appPort + "/actuator/prometheus")).isIn(401, 404);
    }
}
