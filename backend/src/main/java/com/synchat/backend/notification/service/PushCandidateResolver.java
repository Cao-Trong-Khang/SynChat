package com.synchat.backend.notification.service;

import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.notification.model.DeviceRuntimeStates;
import com.synchat.backend.notification.model.PushCandidate;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushCandidateResolver {

    private static final String DEVICE_STATE_KEY_PREFIX = "device_state:";

    private final DeviceRepository deviceRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final StringRedisTemplate redisTemplate;

    public PushCandidateResolver(
            DeviceRepository deviceRepository,
            WebSocketSessionRegistry webSocketSessionRegistry,
            StringRedisTemplate redisTemplate
    ) {
        this.deviceRepository = deviceRepository;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.redisTemplate = redisTemplate;
    }

    public List<PushCandidate> resolveCandidates(UUID userId) {
        return deviceRepository.findByUserId(userId)
                .stream()
                .filter(device -> hasPushToken(device.getPushToken()))
                .map(this::toCandidate)
                .filter(candidate -> shouldPush(candidate.online(), candidate.runtimeState()))
                .toList();
    }

    public boolean shouldPush(boolean online, String runtimeState) {
        if (!online) {
            return true;
        }
        return DeviceRuntimeStates.BACKGROUND.equals(runtimeState);
    }

    private PushCandidate toCandidate(DeviceEntity device) {
        boolean online = !webSocketSessionRegistry.findByDeviceId(device.getId()).isEmpty();
        String runtimeState = redisTemplate.opsForValue().get(DEVICE_STATE_KEY_PREFIX + device.getId());
        if (online && runtimeState == null) {
            runtimeState = DeviceRuntimeStates.FOREGROUND;
        }
        return new PushCandidate(
                device.getUserId(),
                device.getId(),
                device.getPlatform(),
                device.getPushToken(),
                device.getPushProvider(),
                online,
                runtimeState
        );
    }

    private boolean hasPushToken(String pushToken) {
        return pushToken != null && !pushToken.isBlank();
    }
}
