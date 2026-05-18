package com.synchat.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.auth.dto.LoginRequest;
import com.synchat.backend.auth.dto.LoginResponse;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.model.DevicePlatforms;
import com.synchat.backend.device.model.PushProviders;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.notification.NotificationDispatcher;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import com.synchat.backend.user.service.UserService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "app.jwt.access-secret=01234567890123456789012345678901",
                "app.jwt.refresh-secret=01234567890123456789012345678901",
                "app.jwt.access-ttl-minutes=15",
                "app.jwt.refresh-ttl-days=30",
                "app.cors.allowed-origins[0]=http://localhost:3000"
        }
)
@ActiveProfiles("test")
class DeviceRegisterWebSocketIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserCredentialRepository userCredentialRepository;

    @MockBean
    private UserEventOffsetRepository userEventOffsetRepository;

    @MockBean
    private UserEventRepository userEventRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private ConversationRepository conversationRepository;

    @MockBean
    private ConversationMemberRepository conversationMemberRepository;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private NotificationDispatcher notificationDispatcher;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private SyncService syncService;

    @MockBean
    private MessagePaginationService messagePaginationService;

    @MockBean
    private UserService userService;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = org.mockito.Mockito.mock(SetOperations.class);

    private final Map<UUID, UserEntity> usersById = new HashMap<>();
    private final Map<String, UserEntity> usersByEmail = new HashMap<>();
    private final Map<UUID, UserCredentialEntity> credentialsByUserId = new HashMap<>();
    private final Map<UUID, UserEventOffsetEntity> offsetsByUserId = new HashMap<>();
    private final Map<UUID, DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();
    private final List<WebSocketSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        devicesById.clear();
        sessionsById.clear();
        sessions.clear();

        setUpRedis();
        setUpAuthRepositories();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    @Test
    void successfulDeviceRegisterSendsEphemeralRegisteredEventAndUpdatesPushToken() throws Exception {
        LoginFixture fixture = registerAndLogin();
        TestWebSocketHandler socket = connect(fixture);

        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "devices.register",
                  "requestId": "req-device-register-1",
                  "payload": {
                    "deviceId": "%s",
                    "platform": "android",
                    "pushToken": "fcm-token-1",
                    "pushProvider": "fcm"
                  }
                }
                """.formatted(fixture.deviceId())));

        JsonNode response = socket.nextJsonMessage(objectMapper);
        assertDeviceRegistered(response, "req-device-register-1", fixture.deviceId());
        assertThat(devicesById.get(fixture.deviceId()).getPushToken()).isEqualTo("fcm-token-1");
        assertThat(devicesById.get(fixture.deviceId()).getPushProvider()).isEqualTo(PushProviders.FCM);
        assertNoEventsCreated();
    }

    @Test
    void registerWithoutPushTokenSendsRegisteredEventAndClearsPushFields() throws Exception {
        LoginFixture fixture = registerAndLogin();
        TestWebSocketHandler socket = connect(fixture);

        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "devices.register",
                  "requestId": "req-device-register-null",
                  "payload": {
                    "deviceId": "%s",
                    "platform": "android",
                    "pushToken": null,
                    "pushProvider": null
                  }
                }
                """.formatted(fixture.deviceId())));

        JsonNode response = socket.nextJsonMessage(objectMapper);
        assertDeviceRegistered(response, "req-device-register-null", fixture.deviceId());
        assertThat(devicesById.get(fixture.deviceId()).getPushToken()).isNull();
        assertThat(devicesById.get(fixture.deviceId()).getPushProvider()).isNull();
        assertNoEventsCreated();
    }

    @Test
    void mismatchedDeviceIdReturnsForbiddenErrorEnvelope() throws Exception {
        LoginFixture fixture = registerAndLogin();
        TestWebSocketHandler socket = connect(fixture);

        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "devices.register",
                  "requestId": "req-device-mismatch",
                  "payload": {
                    "deviceId": "%s",
                    "platform": "android",
                    "pushToken": "fcm-token-1",
                    "pushProvider": "fcm"
                  }
                }
                """.formatted(UUID.randomUUID())));

        assertError(socket.nextJsonMessage(objectMapper), "req-device-mismatch", "FORBIDDEN");
        assertNoEventsCreated();
    }

    @Test
    void invalidPlatformReturnsValidationErrorEnvelope() throws Exception {
        LoginFixture fixture = registerAndLogin();
        TestWebSocketHandler socket = connect(fixture);

        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "devices.register",
                  "requestId": "req-device-platform",
                  "payload": {
                    "deviceId": "%s",
                    "platform": "ios",
                    "pushToken": "fcm-token-1",
                    "pushProvider": "fcm"
                  }
                }
                """.formatted(fixture.deviceId())));

        assertError(socket.nextJsonMessage(objectMapper), "req-device-platform", "VALIDATION_ERROR");
        assertNoEventsCreated();
    }

    @Test
    void invalidPushProviderReturnsValidationErrorEnvelope() throws Exception {
        LoginFixture fixture = registerAndLogin();
        TestWebSocketHandler socket = connect(fixture);

        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "devices.register",
                  "requestId": "req-device-provider",
                  "payload": {
                    "deviceId": "%s",
                    "platform": "android",
                    "pushToken": "push-token",
                    "pushProvider": "apns"
                  }
                }
                """.formatted(fixture.deviceId())));

        assertError(socket.nextJsonMessage(objectMapper), "req-device-provider", "VALIDATION_ERROR");
        assertNoEventsCreated();
    }

    private void assertDeviceRegistered(JsonNode response, String requestId, UUID deviceId) {
        assertThat(response.path("type").asText()).isEqualTo("device.registered");
        assertThat(response.path("requestId").asText()).isEqualTo(requestId);
        assertThat(response.at("/payload/deviceId").asText()).isEqualTo(deviceId.toString());
    }

    private void assertError(JsonNode response, String requestId, String code) {
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo(requestId);
        assertThat(response.path("error").path("code").asText()).isEqualTo(code);
    }

    private void assertNoEventsCreated() {
        verify(userEventRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private void setUpRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(any())).thenReturn(0L);
    }

    private void setUpAuthRepositories() {
        when(userRepository.existsByEmail(any()))
                .thenAnswer(invocation -> usersByEmail.containsKey(invocation.getArgument(0)));
        when(userRepository.findByEmail(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        when(userRepository.existsById(any()))
                .thenAnswer(invocation -> usersById.containsKey(invocation.getArgument(0)));
        when(userRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            usersById.put(user.getId(), user);
            usersByEmail.put(user.getEmail(), user);
            return user;
        });

        when(userCredentialRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(credentialsByUserId.get(invocation.getArgument(0))));
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

        when(authSessionRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(sessionsById.get(invocation.getArgument(0))));
        when(authSessionRepository.save(any())).thenAnswer(invocation -> {
            AuthSessionEntity session = invocation.getArgument(0);
            sessionsById.put(session.getId(), session);
            return session;
        });
    }

    private TestWebSocketHandler connect(LoginFixture fixture) throws Exception {
        TestWebSocketHandler handler = new TestWebSocketHandler();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(handler, url("/ws?accessToken=%s&deviceId=%s"
                        .formatted(fixture.login().accessToken(), fixture.deviceId())))
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sessions.add(session);
        assertThat(handler.awaitOpen()).isTrue();
        return handler;
    }

    private LoginFixture registerAndLogin() {
        String email = "device.ws." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", "Device User"));
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest(
                email,
                "password123",
                deviceId,
                DevicePlatforms.ANDROID
        ));
        return new LoginFixture(login, deviceId);
    }

    private String url(String path) {
        return "ws://localhost:" + port + path;
    }

    private record LoginFixture(LoginResponse login, UUID deviceId) {
    }

    private static class TestWebSocketHandler extends TextWebSocketHandler {

        private final CountDownLatch openLatch = new CountDownLatch(1);
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            this.session = session;
            openLatch.countDown();
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            openLatch.countDown();
        }

        boolean awaitOpen() throws InterruptedException {
            return openLatch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    && session != null
                    && session.isOpen();
        }

        WebSocketSession session() {
            assertThat(session).isNotNull();
            return session;
        }

        JsonNode nextJsonMessage(ObjectMapper objectMapper) throws Exception {
            String message = messages.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            return objectMapper.readTree(message);
        }
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
