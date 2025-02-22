package com.rizkysiregar.feedzmore.service.firebase;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class FirebaseNotificationService {

    private final WebClient webClient;
    private final FirebaseTokenService tokenService;

    public FirebaseNotificationService(WebClient.Builder webClientBuilder, FirebaseTokenService tokenService) {
        this.webClient = webClientBuilder.baseUrl("https://fcm.googleapis.com/v1").build();
        this.tokenService = tokenService;
    }


    public Mono<String> sendNotificationToToken(String token, String title, String body) {
        return tokenService.getAccessToken().flatMap(accessToken -> {

            Map<String, Object> requestBody = Map.of(
                    "message", Map.of(
                            "token", token,
                            "notification", Map.of(
                                    "title", title,
                                    "body", body
                            )
                    )
            );

            return webClient.post()
                    .uri("/projects/{projectId}/messages:send", "feedzmore")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }

    public Mono<String> sendNotificationToTopic(String topic, String title, String body) {
        log.info("## attempt to send fcm to topic: " + topic + " , title: " + title + ", body: " + body);
        return tokenService.getAccessToken().flatMap(accessToken -> {

            Map<String, Object> requestBody = Map.of(
                    "message", Map.of(
                            "topic", topic,
                            "notification", Map.of(
                                    "title", title,
                                    "body", body
                            )
                    )
            );


            return webClient.post()
                    .uri("/projects/{projectId}/messages:send", "feedzmore")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }


}
