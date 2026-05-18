package com.synchat.backend.notification.model;

import java.util.UUID;

public record PushCandidate(
        UUID userId,
        UUID deviceId,
        String platform,
        String pushToken,
        String pushProvider,
        boolean online,
        String runtimeState
) {
}
