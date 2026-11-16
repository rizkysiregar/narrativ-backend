package com.rizkysiregar.narrativ.service.firebase;


import com.rizkysiregar.narrativ.model.entity.UserDeviceEntity;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FirebaseNotificationService {

    private final WebClient webClient;
    private final FirebaseTokenService tokenService;
    private final UserDeviceService userDeviceService;

    public FirebaseNotificationService(WebClient.Builder webClientBuilder,
                                       FirebaseTokenService tokenService,
                                       UserDeviceService userDeviceService
                                       ) {
        this.webClient = webClientBuilder.baseUrl("https://fcm.googleapis.com/v1").build();
        this.tokenService = tokenService;
        this.userDeviceService = userDeviceService;
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
                    .uri("/projects/{projectId}/messages:send", tokenService.getProjectId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }

    public Mono<String> sendNotificationToTopic(String topic, String title, String body) {
        log.debug("FCM.SEND.TOPIC topic={} title={}", topic, title);
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
                    .uri("/projects/{projectId}/messages:send", tokenService.getProjectId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }

    public void sendNotificationWithUserId(UUID userId, String title, String body) {
        log.debug("FCM.SEND.USER userId={} title={}", userId, title);
        List<UserDeviceEntity> devices = userDeviceService.getActiveDevice(userId);
        for (UserDeviceEntity device : devices) {
            sendNotificationToToken(device.getPushToken(), title, body)
                    .doOnError(ex -> log.error("FCM.SEND.FAILED userId={} deviceId={}", userId, device.getDeviceId(), ex))
                    .subscribe();
        }
    }

}
