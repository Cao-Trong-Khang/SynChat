package com.synchat.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synchat.backend.auth.dto.LoginRequest;
import com.synchat.backend.auth.dto.LoginResponse;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.device.dto.DeviceRegisterCommandPayload;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.model.DevicePlatforms;
import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.device.service.DeviceService;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.synchat.backend.realtime.typing.service.TypingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "app.jwt.access-secret=01234567890123456789012345678901",
        "app.jwt.refresh-secret=01234567890123456789012345678901",
        "app.jwt.access-ttl-minutes=15",
        "app.jwt.refresh-ttl-days=30",
        "app.cors.allowed-origins[0]=http://localhost:3000"
})
@Transactional
class DeviceServiceIntegrationTests {

    @MockBean
    private TypingService typingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private DeviceService deviceService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserCredentialRepository userCredentialRepository;

    @MockBean
    private UserEventOffsetRepository userEventOffsetRepository;

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private ConversationMetadataMapper conversationMetadataMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private SyncService syncService;

    @MockBean
    private MessagePaginationService messagePaginationService;

    @MockBean
    private MessageSendService messageSendService;

    @MockBean
    private OutboxEventAppender outboxEventAppender;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private UserEventAppender userEventAppender;

    @MockBean
    private ReceiptService receiptService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<UUID, UserEntity> usersById = new HashMap<>();
    private final Map<String, UserEntity> usersByEmail = new HashMap<>();
    private final Map<UUID, UserCredentialEntity> credentialsByUserId = new HashMap<>();
    private final Map<UUID, UserEventOffsetEntity> offsetsByUserId = new HashMap<>();
    private final Map<UUID, DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        devicesById.clear();
        sessionsById.clear();

        when(userRepository.existsByEmail(any()))
                .thenAnswer(invocation -> usersByEmail.containsKey(invocation.getArgument(0)));
        when(userRepository.findByEmail(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            usersById.put(user.getId(), user);
            usersByEmail.put(user.getEmail(), user);
            return user;
        });

        when(userCredentialRepository.findById(any())).thenAnswer(
                invocation -> Optional.ofNullable(credentialsByUserId.get(invocation.getArgument(0))));
        when(userCredentialRepository.save(any())).thenAnswer(invocation -> {
            UserCredentialEntity credential = invocation.getArgument(0);
            credentialsByUserId.put(credential.getUserId(), credential);
            return credential;
        });

        when(userEventOffsetRepository.save(any())).thenAnswer(invocation -> {
            UserEventOffsetEntity offset = invocation.getArgument(0);
            offsetsByUserId.put(offset.getUserId(), offset);
            return offset;
        });

        when(deviceRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(devicesById.get(invocation.getArgument(0))));
        when(deviceRepository.save(any())).thenAnswer(invocation -> {
            DeviceEntity device = invocation.getArgument(0);
            devicesById.put(device.getId(), device);
            return device;
        });

        when(authSessionRepository.save(any())).thenAnswer(invocation -> {
            AuthSessionEntity session = invocation.getArgument(0);
            sessionsById.put(session.getId(), session);
            return session;
        });
    }

    @Test
    void registerDeviceWithPushTokenUpdatesExistingDevice() {
        LoginFixture fixture = registerAndLogin();

        deviceService.registerDevice(fixture.userId(), fixture.deviceId(), registerPayload(
                fixture.deviceId(),
                "fcm-token-1",
                PushProviders.FCM
        ));

        DeviceEntity device = devicesById.get(fixture.deviceId());
        assertThat(device.getPlatform()).isEqualTo(DevicePlatforms.ANDROID);
        assertThat(device.getPushToken()).isEqualTo("fcm-token-1");
        assertThat(device.getPushProvider()).isEqualTo(PushProviders.FCM);
        assertNoSideEffectsFromDeviceRegistration();
    }

    @Test
    void registerDeviceWithNullPushTokenClearsPushFields() {
        LoginFixture fixture = registerAndLogin();
        deviceService.registerDevice(fixture.userId(), fixture.deviceId(), registerPayload(
                fixture.deviceId(),
                "fcm-token-1",
                PushProviders.FCM
        ));

        deviceService.registerDevice(fixture.userId(), fixture.deviceId(), registerPayload(
                fixture.deviceId(),
                null,
                null
        ));

        DeviceEntity device = devicesById.get(fixture.deviceId());
        assertThat(device.getPushToken()).isNull();
        assertThat(device.getPushProvider()).isNull();
        assertNoSideEffectsFromDeviceRegistration();
    }

    @Test
    void registerDeviceUpdatesRotatedFcmToken() {
        LoginFixture fixture = registerAndLogin();
        deviceService.registerDevice(fixture.userId(), fixture.deviceId(), registerPayload(
                fixture.deviceId(),
                "fcm-token-1",
                PushProviders.FCM
        ));

        deviceService.registerDevice(fixture.userId(), fixture.deviceId(), registerPayload(
                fixture.deviceId(),
                "fcm-token-2",
                PushProviders.FCM
        ));

        assertThat(devicesById.get(fixture.deviceId()).getPushToken()).isEqualTo("fcm-token-2");
        assertNoSideEffectsFromDeviceRegistration();
    }

    @Test
    void deviceIdMismatchThrowsForbidden() {
        LoginFixture fixture = registerAndLogin();

        assertAppException(
                fixture,
                new DeviceRegisterCommandPayload(UUID.randomUUID(), DevicePlatforms.ANDROID, null, null),
                ErrorCode.FORBIDDEN
        );
    }

    @Test
    void invalidPlatformThrowsValidationError() {
        LoginFixture fixture = registerAndLogin();

        assertAppException(
                fixture,
                new DeviceRegisterCommandPayload(fixture.deviceId(), "ios", null, null),
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    void pushTokenPresentButProviderMissingThrowsValidationError() {
        LoginFixture fixture = registerAndLogin();

        assertAppException(
                fixture,
                registerPayload(fixture.deviceId(), "fcm-token-1", null),
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    void pushProviderPresentButTokenMissingThrowsValidationError() {
        LoginFixture fixture = registerAndLogin();

        assertAppException(
                fixture,
                registerPayload(fixture.deviceId(), null, PushProviders.FCM),
                ErrorCode.VALIDATION_ERROR
        );
    }

    @Test
    void invalidPushProviderThrowsValidationError() {
        LoginFixture fixture = registerAndLogin();

        assertAppException(
                fixture,
                registerPayload(fixture.deviceId(), "push-token", "apns"),
                ErrorCode.VALIDATION_ERROR
        );
    }

    private void assertAppException(
            LoginFixture fixture,
            DeviceRegisterCommandPayload payload,
            ErrorCode expectedCode
    ) {
        assertThatThrownBy(() -> deviceService.registerDevice(fixture.userId(), fixture.deviceId(), payload))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
        assertNoSideEffectsFromDeviceRegistration();
    }

    private DeviceRegisterCommandPayload registerPayload(UUID deviceId, String pushToken, String pushProvider) {
        return new DeviceRegisterCommandPayload(deviceId, DevicePlatforms.ANDROID, pushToken, pushProvider);
    }

    private LoginFixture registerAndLogin() {
        String email = "device." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", "Device User"));
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest(email, "password123", deviceId, "android"));
        return new LoginFixture(login.user().id(), deviceId);
    }

    private void assertNoSideEffectsFromDeviceRegistration() {
        assertThat(sessionsById).hasSize(1);
        assertThat(sessionsById.values().iterator().next().getRevokedAt()).isNull();
        verify(userEventAppender, never()).append(any(), any(), any(), any());
        verify(outboxEventAppender, never()).append(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    private record LoginFixture(UUID userId, UUID deviceId) {
    }

    @TestConfiguration
    static class TransactionTestConfiguration {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };
        }
    }
}
