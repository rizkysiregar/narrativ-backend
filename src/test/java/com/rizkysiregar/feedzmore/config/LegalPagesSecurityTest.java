package com.rizkysiregar.feedzmore.config;

import com.rizkysiregar.feedzmore.controller.LegalController;
import com.rizkysiregar.feedzmore.filter.JwtFilter;
import com.rizkysiregar.feedzmore.filter.RateLimitFilter;
import com.rizkysiregar.feedzmore.service.LegalPageService;
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

/** The legal pages are readable without a login, and only those three GET paths: everything else stays closed. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=legal-pages-test",
                "app.cors.allowed-origins=http://localhost:3000",
                "app.legal.contact-email=help@feedzmore.test",
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
class LegalPagesSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, LegalController.class, LegalPageService.class})
    static class TestApp {
    }

    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JsonAccessDeniedHandler accessDeniedHandler;
    @MockitoBean JwtFilter jwtFilter;
    @MockitoBean RateLimitFilter rateLimitFilter;

    @Value("${local.server.port}") int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void passThroughFilters() throws Exception {
        for (Object filter : new Object[]{jwtFilter, rateLimitFilter}) {
            doAnswer(call -> {
                call.<FilterChain>getArgument(2).doFilter(call.getArgument(0), call.getArgument(1));
                return null;
            }).when((jakarta.servlet.Filter) filter).doFilter(any(), any(), any());
        }
    }

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void allThreePagesAreOpenWithoutLoginAndLockedDown() throws Exception {
        for (String path : new String[]{"/legal/privacy-policy", "/legal/terms", "/legal/delete-account"}) {
            HttpResponse<String> response = send("GET", path);

            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
            assertThat(response.headers().firstValue("Content-Security-Policy").orElse(""))
                    .contains("default-src 'none'").contains("frame-ancestors 'none'");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
            assertThat(response.body()).contains("help@feedzmore.test").doesNotContain("\"code\"");
        }
    }

    @Test
    void languageFollowsTheParameter() throws Exception {
        assertThat(send("GET", "/legal/terms?lang=en").body()).contains("<html lang=\"en\"").contains("Terms &amp; Conditions");
        assertThat(send("GET", "/legal/terms").body()).contains("<html lang=\"id\"");
        assertThat(send("GET", "/legal/terms", "Accept-Language", "en-US").body()).contains("<html lang=\"en\"");
    }

    @Test
    void themeCanBeForcedButNeverInjected() throws Exception {
        assertThat(send("GET", "/legal/terms?theme=dark").body()).contains("data-theme=\"dark\"");
        assertThat(send("GET", "/legal/terms?theme=%22%3E%3Cscript%3E").body())
                .contains("data-theme=\"auto\"").doesNotContain("<script>");
    }

    @Test
    void onlyGetIsOpen() throws Exception {
        assertThat(send("POST", "/legal/privacy-policy").statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/legal/terms").statusCode()).isEqualTo(401);
    }

    @Test
    void noWildcardUnderLegalAndTheRestStaysClosed() throws Exception {
        assertThat(send("GET", "/legal/other").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/legal/").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/legal/privacy-policy/extra").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/posts").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/users/me").statusCode()).isEqualTo(401);
    }
}
