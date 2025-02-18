package com.rizkysiregar.feedzmore.service.firebase;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.internal.FirebaseService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;


@Slf4j
@Service
public class FCMService {
    private static final String FCM_SEND_ENDPOINT = "https://fcm.googleapis.com/v1/projects/feedzmore/messages:send";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private String cachedAccessToken;
    private Instant tokenExpiration;

    private final WebClient webClient;

    public FCMService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://fcm.googleapis.com/v1").build();
    }


//    public Mono<String> sendNotificationToToken(String token, String title, String body) {
//        return "";
//    }

    @PostConstruct
    public void init() {
        log.info("## trying to get fcm token");
        getCachedToken().subscribe(token -> {
            log.info("## fcm token > " + token);
        });
    }

//    @Scheduled(cron = "0 * * * * *") // Runs every 1 minute
//    private void testCronJob() {
//        log.info("## test cronjob");
//    }


    //    0 → At second 0
    //            0 → At minute 0
    //            */8 → Every 8 hours (0, 8, 16, 24)
    //* * * → Every day, every month, any day of the week
    @Scheduled(cron = "0 0 */8 * * *")
    public void sendDailyBroadcastMessage() {
        getCachedToken().subscribe(token -> {
            webClient.post()
                    .uri("/projects/{projectId}/messages:send", "feedzmore")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .bodyValue(buildRequestBody("daily-news", "Daily Update!", "Good morning! Here’s your daily update.", true))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> System.out.println("Daily notification sent: " + response));
        });
    }

    private String buildRequestBody(String target, String title, String body, boolean isTopic) {
        return String.format("""
                    {
                      "message": {
                        "%s": "%s",
                        "notification": {
                          "title": "%s",
                          "body": "%s"
                        },
                        "data": {
                          "extraInfo": "some_data"
                        }
                      }
                    }
                """, isTopic ? "topic" : "token", target, title, body);
    }


    @Cacheable(value = "firebaseTokenCache")
    public Mono<String> getCachedToken() {
        return Mono.fromCallable(() -> {
            try {
                String adminSdkKey = "{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"feedzmore\",\n" +
                        "  \"private_key_id\": \"b9731d99a46340d8ab29d1d4dc990a86dd677dec\",\n" +
                        "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCovQbX9rVuAiEn\\nSdaJIDA6PRrw3+ofE5ddRqnz0mqoNmnT47/5C6QbxQSx7qBzm5yOssdKCDMfQHiF\\nOzVzRZTRK/XOhuzmoDVQI+k6tsgecxMIPsU3gUS4BORHPwBzEPe8TL7vxyRzRiBq\\nU9O6vcIpXhqfla2UKmyTC6nO9abN1BmwIA1HjWujBXorvJUR6In8VismeW1+0eUI\\ncZkD24YkPbF+nED8cwvdorX49SwVX9PGmOdz4NjIq/GaB/2uRhvoPmLOqel8DNq3\\nkTgiZsy96Y+ExnYkRLcV3Ft+1et9sUzrYVu0RIcBIlllbHhl2GTmT6rF/eh6cAM+\\nuZKw09WDAgMBAAECggEABSoX6gc5K/9GWtNQjMzeklcUGoLilwXnUD8H7O2n6bMU\\npRwSNZdMyV7L0yhTDTFvkWcuYKPZ8LRtRo1L3asnsAk7kjvLR2RoN/uJZAx7w9bQ\\nzSP4BEgMEuJvcYYfnOX24cgANgrZy5S+DD25kWcaaLVidOJEuEVeIlgCiqeLwz6U\\nZAmmT2pmGJlG1h+NJOefJ11OQJ7GoTXlXYBcVoLFLmehcqrJ6DIuzn0yDuJcCXo2\\nAL1/9Bau9zhcIKYUcVxKD8vrxwOgfMYqQdlwXSM5hZKb8bZRAJ5J9BVFKluzDVCT\\nNnyi1w37tJsM0AaD9GDsf+2HzxROUhkg5in8q24OLQKBgQDX6jFi1H1/jNhHyvo6\\ngOFPCQReCdf7+Hj+p7yMA40Ivr96EH+TZBcREYlVtfx7+HdPzeG6bT921X2BxXIp\\n4i+ueSilax1Ac+10EquRIW8JGCxjOLoLJ0baSWBNc6zxIxB3FgyZ9Yp8GTFGc0u6\\nQzQCLlQXxbISOJa/CDo2SYEj5QKBgQDIEKyovUspSNGGmpFPgtPfuDuqBg7lF+y5\\nyPNEv5Xk8axvwtOffoZERIQAw7yxEdY5hzubhNuoteZYuotD3Ku9mC7qYsvAMAIV\\nykRYoW1aVnyXW2T21YR6tj+yn90X61eH6J9FXmPsqw1KSLlYc2dJ6fzXcCsn3bsw\\nb4/IR+tNRwKBgBclwlmoEjREc0NvHVh8TKteehtizsbyonYaoXoZP1t6p6h8cFDq\\n4BYyXRjVQ9Y+kvv5RIPqBm3FAYmSE2UuHJKF5relPiOUyhYxkjci8HZ3IvsYL8nW\\nUiBA0ZQ8HIRVkARUVtGCo3Aee0vkPHAhmEfLk7Rgu3B8pJyiUytSwgeJAoGAEnvv\\n59stYZAXw67uNAFrX+UephPPNn1iYrvdNgHZcICfpOfEM501/IK0C3oKKfXy6/fB\\nmxnITasYsCP0D/4IZAj2dLNSDdyhpK++Zs3rU4hgFH/eMSwDBG9oLTG19KrKuITW\\niksxQkF/hdh4P2LSLTMQKDt10UYcMFpuV1vFsqsCgYAVjG1KXUa0eJgDN+2n2EE5\\nI4vCEiANDEehZoyInQfW7bhu7Gr7dIk+KYI13rNhNbsRfEB673DNZxfQW+CQ5Z8R\\n/NNdxUPvjcaFxbyhotAPojzKAnf75mdzP4CcK57r6dqyZYAGzhEN/AtJKn1/sD4P\\nMhE3r6LgqStydiBWl3e5CA==\\n-----END PRIVATE KEY-----\\n\",\n" +
                        "  \"client_email\": \"firebase-adminsdk-fbsvc@feedzmore.iam.gserviceaccount.com\",\n" +
                        "  \"client_id\": \"116312157794454004945\",\n" +
                        "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                        "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                        "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                        "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40feedzmore.iam.gserviceaccount.com\",\n" +
                        "  \"universe_domain\": \"googleapis.com\"\n" +
                        "}\n";

                InputStream inputStream = new ByteArrayInputStream(adminSdkKey.getBytes(StandardCharsets.UTF_8));
                GoogleCredentials googleCredentials = GoogleCredentials
                        .fromStream(inputStream)
                        .createScoped(Arrays.asList(SCOPE));

                googleCredentials.refreshIfExpired();
                AccessToken token = googleCredentials.getAccessToken();
                long expiresInMs = token.getExpirationTime().getTime() - System.currentTimeMillis();

                if (expiresInMs <= 0) {
                    throw new RuntimeException("Access Token expired!");
                }

                return token.getTokenValue();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load Firebase credentials", e);
            }
        });
    }


    /*
    public String getAccessToken() throws IOException {

        if (cachedAccessToken != null && tokenExpiration != null && Instant.now().isBefore(tokenExpiration)) {
            return cachedAccessToken;
        }

        String adminSdkKey = "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"feedzmore\",\n" +
                "  \"private_key_id\": \"b9731d99a46340d8ab29d1d4dc990a86dd677dec\",\n" +
                "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCovQbX9rVuAiEn\\nSdaJIDA6PRrw3+ofE5ddRqnz0mqoNmnT47/5C6QbxQSx7qBzm5yOssdKCDMfQHiF\\nOzVzRZTRK/XOhuzmoDVQI+k6tsgecxMIPsU3gUS4BORHPwBzEPe8TL7vxyRzRiBq\\nU9O6vcIpXhqfla2UKmyTC6nO9abN1BmwIA1HjWujBXorvJUR6In8VismeW1+0eUI\\ncZkD24YkPbF+nED8cwvdorX49SwVX9PGmOdz4NjIq/GaB/2uRhvoPmLOqel8DNq3\\nkTgiZsy96Y+ExnYkRLcV3Ft+1et9sUzrYVu0RIcBIlllbHhl2GTmT6rF/eh6cAM+\\nuZKw09WDAgMBAAECggEABSoX6gc5K/9GWtNQjMzeklcUGoLilwXnUD8H7O2n6bMU\\npRwSNZdMyV7L0yhTDTFvkWcuYKPZ8LRtRo1L3asnsAk7kjvLR2RoN/uJZAx7w9bQ\\nzSP4BEgMEuJvcYYfnOX24cgANgrZy5S+DD25kWcaaLVidOJEuEVeIlgCiqeLwz6U\\nZAmmT2pmGJlG1h+NJOefJ11OQJ7GoTXlXYBcVoLFLmehcqrJ6DIuzn0yDuJcCXo2\\nAL1/9Bau9zhcIKYUcVxKD8vrxwOgfMYqQdlwXSM5hZKb8bZRAJ5J9BVFKluzDVCT\\nNnyi1w37tJsM0AaD9GDsf+2HzxROUhkg5in8q24OLQKBgQDX6jFi1H1/jNhHyvo6\\ngOFPCQReCdf7+Hj+p7yMA40Ivr96EH+TZBcREYlVtfx7+HdPzeG6bT921X2BxXIp\\n4i+ueSilax1Ac+10EquRIW8JGCxjOLoLJ0baSWBNc6zxIxB3FgyZ9Yp8GTFGc0u6\\nQzQCLlQXxbISOJa/CDo2SYEj5QKBgQDIEKyovUspSNGGmpFPgtPfuDuqBg7lF+y5\\nyPNEv5Xk8axvwtOffoZERIQAw7yxEdY5hzubhNuoteZYuotD3Ku9mC7qYsvAMAIV\\nykRYoW1aVnyXW2T21YR6tj+yn90X61eH6J9FXmPsqw1KSLlYc2dJ6fzXcCsn3bsw\\nb4/IR+tNRwKBgBclwlmoEjREc0NvHVh8TKteehtizsbyonYaoXoZP1t6p6h8cFDq\\n4BYyXRjVQ9Y+kvv5RIPqBm3FAYmSE2UuHJKF5relPiOUyhYxkjci8HZ3IvsYL8nW\\nUiBA0ZQ8HIRVkARUVtGCo3Aee0vkPHAhmEfLk7Rgu3B8pJyiUytSwgeJAoGAEnvv\\n59stYZAXw67uNAFrX+UephPPNn1iYrvdNgHZcICfpOfEM501/IK0C3oKKfXy6/fB\\nmxnITasYsCP0D/4IZAj2dLNSDdyhpK++Zs3rU4hgFH/eMSwDBG9oLTG19KrKuITW\\niksxQkF/hdh4P2LSLTMQKDt10UYcMFpuV1vFsqsCgYAVjG1KXUa0eJgDN+2n2EE5\\nI4vCEiANDEehZoyInQfW7bhu7Gr7dIk+KYI13rNhNbsRfEB673DNZxfQW+CQ5Z8R\\n/NNdxUPvjcaFxbyhotAPojzKAnf75mdzP4CcK57r6dqyZYAGzhEN/AtJKn1/sD4P\\nMhE3r6LgqStydiBWl3e5CA==\\n-----END PRIVATE KEY-----\\n\",\n" +
                "  \"client_email\": \"firebase-adminsdk-fbsvc@feedzmore.iam.gserviceaccount.com\",\n" +
                "  \"client_id\": \"116312157794454004945\",\n" +
                "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40feedzmore.iam.gserviceaccount.com\",\n" +
                "  \"universe_domain\": \"googleapis.com\"\n" +
                "}\n";

        InputStream inputStream = new ByteArrayInputStream(adminSdkKey.getBytes(StandardCharsets.UTF_8));
        GoogleCredentials googleCredentials = GoogleCredentials
                .fromStream(inputStream)
                .createScoped(Arrays.asList(SCOPE));

        googleCredentials.refreshIfExpired();
        AccessToken token = googleCredentials.getAccessToken();

        cachedAccessToken = token.getTokenValue();
        tokenExpiration = token.getExpirationTime().toInstant();

        return cachedAccessToken;
    }

     */
}
