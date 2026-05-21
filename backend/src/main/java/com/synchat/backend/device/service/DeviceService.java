package com.synchat.backend.device.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.device.dto.DeviceRegisterCommandPayload;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.model.DevicePlatforms;
import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.device.repository.DeviceRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public void registerDevice(UUID currentUserId, UUID sessionDeviceId, DeviceRegisterCommandPayload payload) {
        ValidatedDeviceRegistration validated = validate(currentUserId, sessionDeviceId, payload);
        Instant now = Instant.now();

        deviceRepository.findById(validated.deviceId())
                .ifPresentOrElse(
                        device -> updateExistingDevice(device, validated, now),
                        () -> createDevice(validated, now)
                );
    }

    private void updateExistingDevice(DeviceEntity device, ValidatedDeviceRegistration validated, Instant now) {
        device.updateLoginOwnerAndPlatform(validated.currentUserId(), validated.platform(), now);
        device.updatePushToken(validated.pushToken(), validated.pushProvider(), now);
        deviceRepository.save(device);
    }

    private void createDevice(ValidatedDeviceRegistration validated, Instant now) {
        deviceRepository.save(DeviceEntity.create(
                validated.deviceId(),
                validated.currentUserId(),
                validated.platform(),
                validated.pushToken(),
                validated.pushProvider(),
                now
        ));
    }

    private ValidatedDeviceRegistration validate(
            UUID currentUserId,
            UUID sessionDeviceId,
            DeviceRegisterCommandPayload payload
    ) {
        if (currentUserId == null) {
            throw validation("Current user is required.");
        }
        if (sessionDeviceId == null) {
            throw validation("Session device is required.");
        }
        if (payload == null) {
            throw validation("Device registration payload is required.");
        }
        if (payload.deviceId() == null) {
            throw validation("deviceId is required.");
        }
        if (!payload.deviceId().equals(sessionDeviceId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Device does not belong to current session.");
        }

        String platform = normalizeRequired(payload.platform(), "platform is required.");
        if (!isAllowedPlatform(platform)) {
            throw validation("Invalid platform.");
        }

        String pushToken = normalizeOptional(payload.pushToken());
        String pushProvider = normalizeOptional(payload.pushProvider());
        if (pushToken == null && pushProvider != null) {
            throw validation("Push token is required.");
        }
        if (pushToken == null) {
            pushProvider = null;
        } else {
            if (pushProvider == null) {
                throw validation("Push provider is required.");
            }
            if (!isAllowedPushProvider(pushProvider)) {
                throw validation("Invalid push provider.");
            }
        }

        return new ValidatedDeviceRegistration(
                currentUserId,
                sessionDeviceId,
                platform,
                pushToken,
                pushProvider
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw validation(message);
        }
        return normalized;
    }

    private boolean isAllowedPlatform(String platform) {
        return DevicePlatforms.ANDROID.equals(platform)
                || DevicePlatforms.WEB.equals(platform);
    }

    private boolean isAllowedPushProvider(String pushProvider) {
        return PushProviders.FCM.equals(pushProvider)
                || PushProviders.FCM_WEB.equals(pushProvider);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record ValidatedDeviceRegistration(
            UUID currentUserId,
            UUID deviceId,
            String platform,
            String pushToken,
            String pushProvider
    ) {
    }
}
