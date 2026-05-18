package com.synchat.backend.notification.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.synchat.backend.common.config.AppProperties;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.notification.fcm",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseAdminConfig {

    @Bean
    public FirebaseApp firebaseApp(AppProperties appProperties) throws IOException {
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());

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
