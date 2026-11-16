package com.rizkysiregar.narrativ.service.firebase;


import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gets the OAuth access token used to call FCM. The service account key is never part of the code or the jar:
 * it comes from {@code firebase.credentials-file} (path of a mounted Kubernetes secret) or, for local
 * development, {@code firebase.credentials-json}. Every environment uses the key of its own Firebase project,
 * and the project id is read from that key so a deployment can never push into another project.
 */
@Service
@Slf4j
public class FirebaseTokenService {
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private long tokenExpirationTime = 0;
    private final GoogleCredentials credentials;
    private final String projectId;

    public FirebaseTokenService(@Value("${firebase.credentials-file:}") String credentialsFile,
                                @Value("${firebase.credentials-json:}") String credentialsJson) {
        ServiceAccountCredentials serviceAccount = load(credentialsFile, credentialsJson);
        if (serviceAccount.getProjectId() == null || serviceAccount.getProjectId().isBlank()) {
            throw new IllegalStateException("The Firebase credentials do not contain a project_id");
        }
        this.projectId = serviceAccount.getProjectId();
        this.credentials = serviceAccount.createScoped(Collections.singletonList(SCOPE));
        log.info("FCM.CREDENTIALS.LOADED project={}", projectId);
    }

    @PostConstruct
    public void init() {
        log.debug("FCM.TOKEN.INIT");
        refreshToken();
    }

    /** Firebase project of the loaded key, used in the FCM send url. */
    public String getProjectId() {
        return projectId;
    }

    public Mono<String> getAccessToken() {
        return Mono.fromSupplier(() -> {
            if (System.currentTimeMillis() >= tokenExpirationTime) {
                // refresh token
                log.debug("FCM.TOKEN.REFRESH");
                refreshToken();
            }

            return cachedToken.get();
        });
    }


    private synchronized void refreshToken() {
        try {
            credentials.refresh();
            AccessToken token = credentials.getAccessToken();
            cachedToken.set(token.getTokenValue());
            tokenExpirationTime = token.getExpirationTime().getTime();
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh Firebase access token", e);
        }
    }

    private static ServiceAccountCredentials load(String credentialsFile, String credentialsJson) {
        try (InputStream stream = open(credentialsFile, credentialsJson)) {
            GoogleCredentials loaded = GoogleCredentials.fromStream(stream);
            if (loaded instanceof ServiceAccountCredentials serviceAccount) {
                return serviceAccount;
            }
            throw new IllegalStateException("The Firebase credentials must be a service account key");
        } catch (IOException e) {
            throw new IllegalStateException("The Firebase credentials cannot be read: " + e.getMessage(), e);
        }
    }

    private static InputStream open(String credentialsFile, String credentialsJson) throws IOException {
        if (credentialsFile != null && !credentialsFile.isBlank()) {
            return Files.newInputStream(Path.of(credentialsFile.trim()));
        }
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException(
                "Firebase credentials are not configured: set FIREBASE_CREDENTIALS_FILE (or FIREBASE_CREDENTIALS_JSON)");
    }

}
