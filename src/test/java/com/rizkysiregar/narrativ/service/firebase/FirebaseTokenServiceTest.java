package com.rizkysiregar.narrativ.service.firebase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseTokenServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsProjectIdFromTheKeyFile() throws Exception {
        Path file = tempDir.resolve("credentials.json");
        Files.writeString(file, serviceAccountJson("narrativ-prod"));

        FirebaseTokenService service = new FirebaseTokenService(file.toString(), "");

        assertEquals("narrativ-prod", service.getProjectId());
    }

    @Test
    void readsTheKeyFromJsonWhenThereIsNoFile() throws Exception {
        FirebaseTokenService service = new FirebaseTokenService("", serviceAccountJson("narrativ"));

        assertEquals("narrativ", service.getProjectId());
    }

    @Test
    void theFileWinsOverTheJson() throws Exception {
        Path file = tempDir.resolve("credentials.json");
        Files.writeString(file, serviceAccountJson("from-file"));

        FirebaseTokenService service = new FirebaseTokenService(file.toString(), serviceAccountJson("from-json"));

        assertEquals("from-file", service.getProjectId());
    }

    @Test
    void refusesToStartWithoutCredentials() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new FirebaseTokenService("", ""));

        assertTrue(e.getMessage().contains("FIREBASE_CREDENTIALS_FILE"));
    }

    @Test
    void refusesToStartWhenTheFileIsMissing() {
        String missing = tempDir.resolve("nope.json").toString();

        assertThrows(IllegalStateException.class, () -> new FirebaseTokenService(missing, ""));
    }

    @Test
    void refusesToStartWhenTheFileIsNotAServiceAccountKey() throws Exception {
        Path file = tempDir.resolve("credentials.json");
        Files.writeString(file, "{\"type\":\"authorized_user\",\"client_id\":\"x\",\"client_secret\":\"y\",\"refresh_token\":\"z\"}");

        assertThrows(IllegalStateException.class, () -> new FirebaseTokenService(file.toString(), ""));
    }

    // a throw-away key: the test never talks to Google
    private static String serviceAccountJson(String projectId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return """
                {
                  "type": "service_account",
                  "project_id": "%s",
                  "private_key_id": "test-key-id",
                  "private_key": "%s",
                  "client_email": "test@%s.iam.gserviceaccount.com",
                  "client_id": "1",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.formatted(projectId, pem.replace("\n", "\\n"), projectId);
    }
}
