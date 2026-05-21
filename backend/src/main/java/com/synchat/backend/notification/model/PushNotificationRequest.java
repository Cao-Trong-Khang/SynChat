package com.synchat.backend.notification.model;

import java.util.Map;
import java.util.UUID;

public record PushNotificationRequest(
        UUID userId,
        UUID deviceId,
        String pushToken,
        String pushProvider,
        String title,
        String body,
        Map<String, String> data
) {
}
