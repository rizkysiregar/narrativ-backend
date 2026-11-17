package com.rizkysiregar.narrativ.service.firebase;

import com.rizkysiregar.narrativ.model.entity.UserDeviceEntity;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
public class FirebaseNotificationServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private FirebaseTokenService firebaseTokenService;

    @Mock
    private UserDeviceService userDeviceService;

    private FirebaseNotificationService notificationService;

    @BeforeEach
    void setup() {
        // WebClient builder is used in constructor via @InjectMocks, so stubbing must be ready here
        when(webClientBuilder.baseUrl("https://fcm.googleapis.com/v1")).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        // Manually construct service after stubbing builder
        notificationService = new FirebaseNotificationService(webClientBuilder, firebaseTokenService, userDeviceService);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void sendNotificationToToken_Success() {
        // Stub token service
        when(firebaseTokenService.getAccessToken()).thenReturn(Mono.just("ACCESS_TOKEN"));
        when(firebaseTokenService.getProjectId()).thenReturn("narrativ");

        // Explicitly stub the WebClient fluent chain
        WebClient.RequestBodyUriSpec postSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/projects/{projectId}/messages:send", "narrativ")).thenReturn(postSpec);
        when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);

        when(postSpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));

        // Act
        String result = notificationService
                .sendNotificationToToken("DEVICE_TOKEN", "Hello", "World")
                .block();

        // Assert
        Assertions.assertEquals("OK", result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendNotificationToToken_includesDataPayload() {
        when(firebaseTokenService.getAccessToken()).thenReturn(Mono.just("ACCESS_TOKEN"));
        when(firebaseTokenService.getProjectId()).thenReturn("narrativ");
        WebClient.RequestBodyUriSpec postSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/projects/{projectId}/messages:send", "narrativ")).thenReturn(postSpec);
        when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        when(postSpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));

        notificationService.sendNotificationToToken("T", "Hello", "World", Map.of("postId", "42")).block();

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(postSpec).bodyValue(body.capture());
        Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) body.getValue()).get("message");
        Assertions.assertEquals(Map.of("postId", "42"), message.get("data"));
        Assertions.assertEquals("T", message.get("token"));
        Assertions.assertEquals(Map.of("priority", "HIGH"), message.get("android"));
    }

    private UserDeviceEntity device(String token, String locale) {
        UserDeviceEntity device = new UserDeviceEntity();
        device.setPushToken(token);
        device.setDeviceId("d-" + token);
        device.setLocale(locale);
        return device;
    }

    @Test
    void sendToUserDevices_deactivatesUnregisteredTokensAndUsesDeviceLocale() {
        UUID userId = UUID.randomUUID();
        when(userDeviceService.getActiveDevice(userId))
                .thenReturn(List.of(device("dead", "id-ID"), device("alive", "en-US")));
        FirebaseNotificationService spy = Mockito.spy(notificationService);
        Mockito.doReturn(Mono.error(WebClientResponseException.create(404, "Not Found", null,
                        "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"
                                .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)))
                .when(spy).sendNotificationToToken(eq("dead"), anyString(), anyString(), any());
        Mockito.doReturn(Mono.just("OK"))
                .when(spy).sendNotificationToToken(eq("alive"), anyString(), anyString(), any());

        List<Locale> locales = new java.util.ArrayList<>();
        FirebaseNotificationService.DeliveryResult result = spy.sendNotificationToUserDevices(userId, locale -> {
            locales.add(locale);
            return new FirebaseNotificationService.PushText("t", "b");
        }, Map.of());

        Assertions.assertEquals(new FirebaseNotificationService.DeliveryResult(1, 1, 0), result);
        Assertions.assertEquals(List.of(Locale.forLanguageTag("id-ID"), Locale.forLanguageTag("en-US")), locales);
        Mockito.verify(userDeviceService).deactivateDevice("dead");
        Mockito.verify(userDeviceService, Mockito.never()).deactivateDevice("alive");
    }

    @Test
    void sendToUserDevices_throwsAfterTryingAllDevicesWhenFcmFailsTemporarily() {
        UUID userId = UUID.randomUUID();
        when(userDeviceService.getActiveDevice(userId))
                .thenReturn(List.of(device("a", null), device("b", null)));
        FirebaseNotificationService spy = Mockito.spy(notificationService);
        Mockito.doReturn(Mono.error(WebClientResponseException.create(503, "Unavailable", null, new byte[0], null)))
                .when(spy).sendNotificationToToken(eq("a"), anyString(), anyString(), any());
        Mockito.doReturn(Mono.just("OK"))
                .when(spy).sendNotificationToToken(eq("b"), anyString(), anyString(), any());

        Assertions.assertThrows(IllegalStateException.class, () -> spy.sendNotificationToUserDevices(userId,
                locale -> new FirebaseNotificationService.PushText("t", "b"), Map.of()));

        Mockito.verify(spy).sendNotificationToToken(eq("b"), anyString(), anyString(), any());
        Mockito.verify(userDeviceService, Mockito.never()).deactivateDevice(anyString());
    }

}
