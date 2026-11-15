package com.rizkysiregar.feedzmore.i18n;

import com.rizkysiregar.feedzmore.adviser.RestErrorHandlerControllerAdviser;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The real wiring: Accept-Language through the filter, the advice, Spring's MessageSource and Bean Validation. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=api-language-test",
                "spring.messages.basename=i18n/messages",
                "spring.messages.fallback-to-system-locale=false",
                "management.tracing.export.zipkin.enabled=false",
                "management.zipkin.tracing.export.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
        })
class ApiLanguageIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({I18nConfig.class, LocaleFilter.class, RestErrorHandlerControllerAdviser.class, Api.class})
    static class TestApp {
    }

    @RestController
    static class Api {
        record Body(@NotBlank(message = "{validation.username.required}") String username) {
        }

        @GetMapping("/missing")
        String missing() {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        @GetMapping("/slow-down")
        String slowDown() {
            throw new BusinessException(GlobalMessage.RATE_LIMIT, HttpStatus.TOO_MANY_REQUESTS, 12L);
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody Body body) {
            return "ok";
        }
    }

    @Value("${local.server.port}") int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String language, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void noHeaderMeansEnglish() throws Exception {
        HttpResponse<String> response = call("GET", "/missing", null, null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"message\":\"Oops, the data you are looking for was not found.\"")
                .contains("\"errorCode\":\"NOT_FOUND\"");
        assertThat(response.headers().firstValue("Content-Language")).contains("en");
    }

    @Test
    void indonesianHeaderGivesIndonesianText() throws Exception {
        HttpResponse<String> response = call("GET", "/missing", "id-ID,id;q=0.9", null);

        assertThat(response.body()).contains("\"message\":\"Ups, data yang Anda cari tidak ditemukan.\"")
                .contains("\"errorCode\":\"NOT_FOUND\"");
        assertThat(response.headers().firstValue("Content-Language")).contains("id");
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() throws Exception {
        assertThat(call("GET", "/missing", "fr-FR", null).body()).contains("Oops, the data you are looking for was not found.");
    }

    @Test
    void argsAreWordedInTheLanguageAndExposedToTheClient() throws Exception {
        HttpResponse<String> response = call("GET", "/slow-down", "id", null);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.body()).contains("Coba lagi 12 detik lagi ya!").contains("\"args\":[\"12\"]");
    }

    @Test
    void validationMessagesFollowTheLanguageToo() throws Exception {
        assertThat(call("POST", "/validate", "id", "{\"username\":\"\"}").body())
                .contains("username: Username wajib diisi");
        assertThat(call("POST", "/validate", "en", "{\"username\":\"\"}").body())
                .contains("username: Username is required");
    }
}
