package com.synchat.backend.notification.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.synchat.backend.common.config.AppProperties;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.notification.fcm", name = "enabled", havingValue = "true")
public class FirebaseAdminConfig {

    @Bean
    public FirebaseApp firebaseApp(AppProperties appProperties) throws IOException {
        InputStream serviceAccount;
        String firebaseJson = System.getenv("FIREBASE_CREDENTIALS_JSON");

        // Read directly from JSON string if available (Production/Railway environment)
        if (firebaseJson != null && !firebaseJson.isBlank()) {
            serviceAccount = new ByteArrayInputStream(firebaseJson.getBytes(StandardCharsets.UTF_8));
        } else {
            // Fallback to reading from local file path (Local environment)
            String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (path == null) {
                throw new IllegalStateException("Missing Firebase credentials (JSON or Path)!");
            }
            serviceAccount = new FileInputStream(path);
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount));

        String projectId = appProperties.notification().fcm().projectId();
        if (projectId != null && !projectId.isBlank()) {
            optionsBuilder.setProjectId(projectId);
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().getFirst();
        }

        return FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}