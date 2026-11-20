package com.rizkysiregar.narrativ.config;

import com.rizkysiregar.narrativ.controller.PublicController;
import com.rizkysiregar.narrativ.filter.AppCheckFilter;
import com.rizkysiregar.narrativ.filter.JwtFilter;
import com.rizkysiregar.narrativ.filter.RateLimitFilter;
import com.rizkysiregar.narrativ.service.PublicContentService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Shared links are readable without a login, for GET only; everything else stays closed. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=public-pages-test",
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
class PublicPagesSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, PublicController.class})
    static class TestApp {
    }

    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JsonAccessDeniedHandler accessDeniedHandler;
    @MockitoBean JwtFilter jwtFilter;
    @MockitoBean RateLimitFilter rateLimitFilter;
    @MockitoBean AppCheckFilter appCheckFilter;
    @MockitoBean PublicContentService publicContentService;

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

    private HttpResponse<String> send(String method, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readableWithoutLogin() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(publicContentService.mediaImageUrl(mediaId)).thenReturn("https://storage.test/x.jpg?signed");

        assertThat(send("GET", "/public/users/ani").statusCode()).isNotIn(401, 403);
        assertThat(send("GET", "/public/posts/" + UUID.randomUUID()).statusCode()).isNotIn(401, 403);

        HttpResponse<String> image = send("GET", "/public/media/" + mediaId);
        assertThat(image.statusCode()).isEqualTo(302);
        assertThat(image.headers().firstValue("Location")).hasValue("https://storage.test/x.jpg?signed");
        assertThat(image.headers().firstValue("Cache-Control").orElse("")).contains("max-age=600").contains("public");
    }

    @Test
    void onlyGetIsOpenAndTheRestStaysClosed() throws Exception {
        assertThat(send("POST", "/public/users/ani").statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/public/posts/" + UUID.randomUUID()).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/posts").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/users/ani").statusCode()).isEqualTo(401);
    }
}
