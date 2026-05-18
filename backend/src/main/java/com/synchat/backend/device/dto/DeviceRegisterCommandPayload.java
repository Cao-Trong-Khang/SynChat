package com.synchat.backend.device.dto;

import java.util.UUID;

public record DeviceRegisterCommandPayload(
        UUID deviceId,
        String platform,
        String pushToken,
        String pushProvider
) {
}
