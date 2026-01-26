package com.rizkysiregar.feedzmore.service.firebase;

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

    private FirebaseNotificationService notificationService;

    @BeforeEach
    void setup() {
        // WebClient builder is used in constructor via @InjectMocks, so stubbing must be ready here
        when(webClientBuilder.baseUrl("https://fcm.googleapis.com/v1")).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        // Manually construct service after stubbing builder
        notificationService = new FirebaseNotificationService(webClientBuilder, firebaseTokenService);
    }
    
    @Test
    void sendNotificationToToken_Success() {
        // Stub token service
        when(firebaseTokenService.getAccessToken()).thenReturn(Mono.just("ACCESS_TOKEN"));

        // Explicitly stub the WebClient fluent chain
        WebClient.RequestBodyUriSpec postSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/projects/{projectId}/messages:send", "feedzmore")).thenReturn(postSpec);
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


}
