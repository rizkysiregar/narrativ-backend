package com.rizkysiregar.narrativ.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.narrativ.annotation.IgnoreResponseBinding;
import com.rizkysiregar.narrativ.config.ResponseEnvelopeCustomizer;
import com.rizkysiregar.narrativ.config.SwaggerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated OpenAPI document matches the wire format of {@code CustomResponseAdvise}: the web client generates
 * its types from it. Lives in the controller package because the envelope only applies there.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=openapi-envelope-test",
                "springdoc.api-docs.enabled=true",
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiResponseEnvelopeTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SwaggerConfig.class, ResponseEnvelopeCustomizer.class, Api.class})
    static class TestApp {
    }

    record Thing(String name) {
    }

    @RestController
    static class Api {
        @GetMapping("/thing")
        Thing thing() {
            return new Thing("a");
        }

        @GetMapping("/things")
        List<Thing> things() {
            return List.of();
        }

        @GetMapping("/entity")
        ResponseEntity<Thing> entity() {
            return ResponseEntity.ok(new Thing("a"));
        }

        @GetMapping("/raw")
        @IgnoreResponseBinding
        Thing raw() {
            return new Thing("a");
        }
    }

    @Value("${local.server.port}") int port;

    private JsonNode doc;

    @BeforeAll
    void loadDocument() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        doc = new ObjectMapper().readTree(response.body());
    }

    private JsonNode okSchema(String path) {
        JsonNode content = doc.at("/paths/" + path.replace("/", "~1") + "/get/responses/200/content");
        assertThat(content.isObject()).as("200 content of %s", path).isTrue();
        return content.elements().next().get("schema");
    }

    @Test
    void successResponsesAreWrappedInTheEnvelope() {
        JsonNode thing = okSchema("/thing");
        assertThat(thing.at("/properties/code").isObject()).isTrue();
        assertThat(thing.at("/properties/message").isObject()).isTrue();
        assertThat(thing.at("/properties/data/$ref").asText()).endsWith("/Thing");
        assertThat(thing.get("required")).extracting(JsonNode::asText).contains("code", "message");

        assertThat(okSchema("/things").at("/properties/data/items/$ref").asText()).endsWith("/Thing");
    }

    @Test
    void passedThroughResponsesStayAsTheyAre() {
        assertThat(okSchema("/entity").at("/$ref").asText()).endsWith("/Thing");
        assertThat(okSchema("/raw").at("/$ref").asText()).endsWith("/Thing");
    }

    @Test
    void errorsAreDocumentedOnce() {
        assertThat(doc.at("/paths/~1thing/get/responses/default/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/" + ResponseEnvelopeCustomizer.ERROR_SCHEMA);
        JsonNode error = doc.at("/components/schemas/" + ResponseEnvelopeCustomizer.ERROR_SCHEMA);
        assertThat(error.at("/properties/errorCode").isObject()).isTrue();
        assertThat(error.at("/properties/args/items").isObject()).isTrue();
    }
}
