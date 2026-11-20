package com.rizkysiregar.narrativ.service.firebase;

import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.config.WebPushProperties;

import com.rizkysiregar.narrativ.model.entity.UserDeviceEntity;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class FirebaseNotificationService {

    private final WebClient webClient;
    private final FirebaseTokenService tokenService;
    private final UserDeviceService userDeviceService;
    private final String webOrigin;

    public FirebaseNotificationService(WebClient.Builder webClientBuilder,
                                       FirebaseTokenService tokenService,
                                       UserDeviceService userDeviceService,
                                       WebPushProperties webPushProperties
                                       ) {
        this.webClient = webClientBuilder.baseUrl("https://fcm.googleapis.com/v1").build();
        this.tokenService = tokenService;
        this.userDeviceService = userDeviceService;
        this.webOrigin = webPushProperties.origin();
    }


    public Mono<String> sendNotificationToToken(String token, String title, String body) {
        return sendNotificationToToken(token, title, body, Map.of());
    }

    /**
     * @param data custom key/value pairs delivered with the push (FCM only accepts string values); the app reads
     *             them to open the right screen when the notification is tapped
     */
    public Mono<String> sendNotificationToToken(String token, String title, String body, Map<String, String> data) {
        return sendNotificationToToken(token, title, body, data, null);
    }

    /**
     * @param webLink for a browser (a WEB device): the absolute page a click on the notification opens; null for apps
     */
    public Mono<String> sendNotificationToToken(String token, String title, String body, Map<String, String> data,
                                                String webLink) {
        return tokenService.getAccessToken().flatMap(accessToken -> {

            Map<String, Object> message = new HashMap<>();
            message.put("token", token);
            message.put("notification", Map.of(
                    "title", title,
                    "body", body
            ));
            if (data != null && !data.isEmpty()) {
                message.put("data", data);
            }
            // high priority so the push is delivered at once, also while the phone is dozing, and may pop up
            // on screen instead of waiting in the shade
            message.put("android", Map.of("priority", "HIGH"));
            if (webLink != null) {
                message.put("webpush", Map.of(
                        "fcm_options", Map.of("link", webLink),
                        "notification", Map.of("icon", webOrigin + "/icon-192.png")));
            }

            return webClient.post()
                    .uri("/projects/{projectId}/messages:send", tokenService.getProjectId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(Map.of("message", message))
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

    /** Title and body of a push, already in the recipient's language. */
    public record PushText(String title, String body) {
    }

    /** What happened to the pushes of one user: devices reached, tokens retired, and transient failures. */
    public record DeliveryResult(int sent, int invalidated, int failed) {
    }

    /** Fire and forget: failures are only logged. */
    public void sendNotificationWithUserId(UUID userId, String title, String body) {
        log.debug("FCM.SEND.USER userId={} title={}", userId, title);
        List<UserDeviceEntity> devices = userDeviceService.getActiveDevice(userId);
        for (UserDeviceEntity device : devices) {
            sendToDevice(userId, device, title, body, Map.of()).subscribe();
        }
    }

    /**
     * Sends to every active device of the user and waits for FCM's answer, so the caller knows whether the push
     * really went out. The text is picked per device from its registered locale. Tokens FCM reports as no longer
     * registered are deactivated.
     *
     * @throws IllegalStateException when a device could not be reached for a reason that may be temporary, after
     *                               all the other devices were tried; a redelivery can then retry
     */
    public DeliveryResult sendNotificationToUserDevices(UUID userId, Function<Locale, PushText> textFor,
                                                        Map<String, String> data) {
        log.debug("FCM.SEND.USER userId={}", userId);
        List<UserDeviceEntity> devices = userDeviceService.getActiveDevice(userId);
        List<Outcome> outcomes = Flux.fromIterable(devices)
                .concatMap(device -> {
                    PushText text = textFor.apply(localeOf(device));
                    return sendToDevice(userId, device, text.title(), text.body(), data);
                })
                .collectList()
                .block(SEND_TIMEOUT.multipliedBy(Math.max(1, devices.size())));
        if (outcomes == null) {
            outcomes = List.of();
        }
        int sent = (int) outcomes.stream().filter(o -> o == Outcome.SENT).count();
        int invalidated = (int) outcomes.stream().filter(o -> o == Outcome.INVALID_TOKEN).count();
        int failed = outcomes.size() - sent - invalidated;
        if (failed > 0) {
            throw new IllegalStateException("FCM send failed for " + failed + " device(s) of user " + userId);
        }
        return new DeliveryResult(sent, invalidated, failed);
    }

    private enum Outcome {SENT, INVALID_TOKEN, FAILED}

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private Mono<Outcome> sendToDevice(UUID userId, UserDeviceEntity device, String title, String body,
                                       Map<String, String> data) {
        Mono<String> send = "WEB".equalsIgnoreCase(device.getPlatform()) && !webOrigin.isEmpty()
                ? sendNotificationToToken(device.getPushToken(), title, body, data, webLink(webOrigin, data))
                : sendNotificationToToken(device.getPushToken(), title, body, data);
        return send
                .timeout(SEND_TIMEOUT)
                .map(response -> Outcome.SENT)
                .defaultIfEmpty(Outcome.SENT)
                .onErrorResume(ex -> {
                    if (isTokenGone(ex)) {
                        log.info("FCM.TOKEN.RETIRED userId={} deviceId={}", userId, device.getDeviceId());
                        userDeviceService.deactivateDevice(device.getPushToken());
                        return Mono.just(Outcome.INVALID_TOKEN);
                    }
                    log.error("FCM.SEND.FAILED userId={} deviceId={}", userId, device.getDeviceId(), ex);
                    return Mono.just(Outcome.FAILED);
                });
    }

    /**
     * The page of the web client a push is about: the follower's profile, the post (its comments for comment events),
     * or the home page when the push names neither.
     */
    static String webLink(String origin, Map<String, String> data) {
        String type = data.getOrDefault("type", "");
        String actor = data.get("actorUsername");
        if (Constants.NOTIFICATION_TYPE_FOLLOWED.equals(type) && actor != null) {
            return origin + "/u/" + UriUtils.encodePathSegment(actor, StandardCharsets.UTF_8);
        }
        String postId = data.get("postId");
        if (postId != null && !postId.isBlank() && !"null".equals(postId)) {
            boolean comments = Constants.NOTIFICATION_TYPE_POST_COMMENTED.equals(type)
                    || Constants.NOTIFICATION_TYPE_COMMENT_REPLIED.equals(type)
                    || Constants.NOTIFICATION_TYPE_COMMENT_PINNED.equals(type);
            return origin + "/p/" + UriUtils.encodePathSegment(postId, StandardCharsets.UTF_8) + (comments ? "#comments" : "");
        }
        return origin + "/";
    }

    /** FCM answers 404 / UNREGISTERED once the app was uninstalled or its token rotated: the token is dead for good. */
    private static boolean isTokenGone(Throwable ex) {
        return ex instanceof WebClientResponseException response
                && (response.getStatusCode().value() == 404
                || response.getResponseBodyAsString().contains("UNREGISTERED"));
    }

    private static Locale localeOf(UserDeviceEntity device) {
        String tag = device.getLocale();
        if (tag == null || tag.isBlank()) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(tag.replace('_', '-'));
    }

}
