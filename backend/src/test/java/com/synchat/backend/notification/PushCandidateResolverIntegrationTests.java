package com.synchat.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.notification.model.DeviceRuntimeStates;
import com.synchat.backend.notification.model.PushCandidate;
import com.synchat.backend.notification.service.PushCandidateResolver;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(classes = PushCandidateResolver.class)
class PushCandidateResolverIntegrationTests {

    private static final String DEVICE_STATE_KEY_PREFIX = "device_state:";

    @Autowired
    private PushCandidateResolver pushCandidateResolver;

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisValues = new HashMap<>();

    private UUID userId;
    private UUID foregroundDeviceId;
    private UUID backgroundDeviceId;
    private UUID offlineDeviceId;
    private UUID noTokenDeviceId;
    private UUID missingStateDeviceId;

    @BeforeEach
    void setUp() {
        redisValues.clear();

        userId = UUID.randomUUID();
        foregroundDeviceId = UUID.randomUUID();
        backgroundDeviceId = UUID.randomUUID();
        offlineDeviceId = UUID.randomUUID();
        noTokenDeviceId = UUID.randomUUID();
        missingStateDeviceId = UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        when(redisTemplate.delete(any(String.class))).thenAnswer(
                invocation -> redisValues.remove(invocation.getArgument(0)) != null);

        when(deviceRepository.findByUserId(userId)).thenReturn(List.of(
                device(foregroundDeviceId, "foreground-token"),
                device(backgroundDeviceId, "background-token"),
                device(offlineDeviceId, "offline-token"),
                device(noTokenDeviceId, null),
                device(missingStateDeviceId, "missing-state-token")
        ));

        when(webSocketSessionRegistry.findByDeviceId(foregroundDeviceId))
                .thenReturn(List.of(binding("socket-foreground", foregroundDeviceId)));
        when(webSocketSessionRegistry.findByDeviceId(backgroundDeviceId))
                .thenReturn(List.of(binding("socket-background", backgroundDeviceId)));
        when(webSocketSessionRegistry.findByDeviceId(offlineDeviceId)).thenReturn(List.of());
        when(webSocketSessionRegistry.findByDeviceId(noTokenDeviceId)).thenReturn(List.of());
        when(webSocketSessionRegistry.findByDeviceId(missingStateDeviceId))
                .thenReturn(List.of(binding("socket-missing-state", missingStateDeviceId)));

        redisTemplate.opsForValue().set(DEVICE_STATE_KEY_PREFIX + foregroundDeviceId, DeviceRuntimeStates.FOREGROUND);
        redisValues.put(DEVICE_STATE_KEY_PREFIX + foregroundDeviceId, DeviceRuntimeStates.FOREGROUND);
        redisValues.put(DEVICE_STATE_KEY_PREFIX + backgroundDeviceId, DeviceRuntimeStates.BACKGROUND);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(DEVICE_STATE_KEY_PREFIX + foregroundDeviceId);
        redisTemplate.delete(DEVICE_STATE_KEY_PREFIX + backgroundDeviceId);
        redisTemplate.delete(DEVICE_STATE_KEY_PREFIX + offlineDeviceId);
        redisTemplate.delete(DEVICE_STATE_KEY_PREFIX + noTokenDeviceId);
        redisTemplate.delete(DEVICE_STATE_KEY_PREFIX + missingStateDeviceId);
        redisValues.clear();
    }

    @Test
    void offlineDeviceWithTokenIsCandidate() {
        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::deviceId)
                .contains(offlineDeviceId);
    }

    @Test
    void onlineBackgroundDeviceWithTokenIsCandidate() {
        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::deviceId)
                .contains(backgroundDeviceId);
    }

    @Test
    void onlineForegroundDeviceWithTokenIsNotCandidate() {
        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::deviceId)
                .doesNotContain(foregroundDeviceId);
    }

    @Test
    void deviceWithoutPushTokenIsNotCandidate() {
        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::deviceId)
                .doesNotContain(noTokenDeviceId);
    }

    @Test
    void onlineDeviceWithMissingDeviceStateDefaultsToForegroundAndIsNotCandidate() {
        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::deviceId)
                .doesNotContain(missingStateDeviceId);
    }

    @Test
    void duplicatePushTokensAreOnlyReturnedOnce() {
        UUID firstDeviceId = UUID.randomUUID();
        UUID secondDeviceId = UUID.randomUUID();
        when(deviceRepository.findByUserId(userId)).thenReturn(List.of(
                device(firstDeviceId, "same-token"),
                device(secondDeviceId, "same-token")
        ));
        when(webSocketSessionRegistry.findByDeviceId(firstDeviceId)).thenReturn(List.of());
        when(webSocketSessionRegistry.findByDeviceId(secondDeviceId)).thenReturn(List.of());

        List<PushCandidate> candidates = pushCandidateResolver.resolveCandidates(userId);

        assertThat(candidates)
                .extracting(PushCandidate::pushToken)
                .containsExactly("same-token");
    }

    private DeviceEntity device(UUID deviceId, String pushToken) {
        return DeviceEntity.create(deviceId, userId, "android", pushToken, PushProviders.FCM, Instant.now());
    }

    private UserSessionBinding binding(String socketId, UUID deviceId) {
        return new UserSessionBinding(socketId, userId, UUID.randomUUID(), deviceId, Instant.now());
    }
}
