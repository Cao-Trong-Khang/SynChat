package com.synchat.backend.realtime.presence;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import com.synchat.backend.realtime.typing.service.TypingService;
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
class PresenceWebSocketIntegrationTests {

    @MockBean
    private TypingService typingService;

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
    private final Map<UUID, com.synchat.backend.device.entity.DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();
    private final List<WebSocketSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        devicesById.clear();
        sessionsById.clear();
        redisSets.clear();
        sessions.clear();

        setUpRedis();
        setUpRepositories();
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
    void subscribeReturnsOfflineSnapshotAndOnlineThenOfflineChanges() throws Exception {
        LoginFixture userA = registerAndLogin("a");
        LoginFixture userB = registerAndLogin("b");
        TestWebSocketHandler socketA = connect(userA);

        sendSubscribe(socketA, "req-presence-subscribe-1", userB.userId());

        JsonNode snapshot = socketA.nextJsonMessage(objectMapper);
        assertThat(snapshot.path("type").asText()).isEqualTo("presence.snapshot");
        assertThat(snapshot.path("requestId").asText()).isEqualTo("req-presence-subscribe-1");
        assertThat(snapshot.at("/payload/items/0/userId").asText()).isEqualTo(userB.userId().toString());
        assertThat(snapshot.at("/payload/items/0/online").asBoolean()).isFalse();
        assertThat(snapshot.at("/payload/items/0/lastSeenAt").isNull()).isTrue();

        TestWebSocketHandler socketB = connect(userB);
        JsonNode onlineChanged = socketA.nextJsonMessage(objectMapper);
        assertPresenceChanged(onlineChanged, userB.userId(), true);

        socketB.session().close();
        awaitUntil(() -> usersById.get(userB.userId()).getLastSeenAt() != null);
        JsonNode offlineChanged = socketA.nextJsonMessage(objectMapper);
        assertPresenceChanged(offlineChanged, userB.userId(), false);
        assertThat(offlineChanged.at("/payload/lastSeenAt").asText()).isNotBlank();
        assertNoPersistedEvents();
    }

    @Test
    void unsubscribeStopsPresenceChangedDelivery() throws Exception {
        LoginFixture userA = registerAndLogin("a");
        LoginFixture userB = registerAndLogin("b");
        TestWebSocketHandler socketA = connect(userA);

        sendSubscribe(socketA, "req-subscribe", userB.userId());
        socketA.nextJsonMessage(objectMapper);
        socketA.session().sendMessage(new TextMessage("""
                {
                  "type": "presence.unsubscribe",
                  "requestId": "req-unsubscribe",
                  "payload": {
                    "userIds": ["%s"]
                  }
                }
                """.formatted(userB.userId())));

        TestWebSocketHandler socketB = connect(userB);
        socketB.session().close();
        awaitUntil(() -> usersById.get(userB.userId()).getLastSeenAt() != null);

        assertThat(socketA.pollJsonMessage(objectMapper, Duration.ofMillis(500))).isNull();
        assertNoPersistedEvents();
    }

    @Test
    void invalidSubscribePayloadReturnsValidationErrorEnvelope() throws Exception {
        LoginFixture userA = registerAndLogin("a");
        TestWebSocketHandler socketA = connect(userA);

        socketA.session().sendMessage(new TextMessage("""
                {
                  "type": "presence.subscribe",
                  "requestId": "req-invalid-presence",
                  "payload": {
                    "userIds": []
                  }
                }
                """));

        JsonNode response = socketA.nextJsonMessage(objectMapper);
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo("req-invalid-presence");
        assertThat(response.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertNoPersistedEvents();
    }

    private void sendSubscribe(TestWebSocketHandler socket, String requestId, UUID userId) throws Exception {
        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "presence.subscribe",
                  "requestId": "%s",
                  "payload": {
                    "userIds": ["%s"]
                  }
                }
                """.formatted(requestId, userId)));
    }

    private void assertPresenceChanged(JsonNode event, UUID userId, boolean online) {
        assertThat(event.path("type").asText()).isEqualTo("presence.changed");
        assertThat(event.at("/payload/userId").asText()).isEqualTo(userId.toString());
        assertThat(event.at("/payload/online").asBoolean()).isEqualTo(online);
        if (online) {
            assertThat(event.at("/payload/lastSeenAt").isNull()).isTrue();
        }
    }

    private void assertNoPersistedEvents() {
        verify(userEventRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private void setUpRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisSets.computeIfAbsent(key, ignored -> new HashSet<>()).add(value);
            return 1L;
        });
        when(setOperations.remove(any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            Set<String> values = redisSets.get(key);
            return values != null && values.remove(value) ? 1L : 0L;
        });
        when(setOperations.members(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Set<String> values = redisSets.get(key);
            return values == null ? null : new HashSet<>(values);
        });
        when(setOperations.size(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Set<String> values = redisSets.get(key);
            return values == null ? 0L : (long) values.size();
        });
        when(redisTemplate.delete(any(String.class))).thenAnswer(invocation -> redisSets.remove(invocation.getArgument(0)) != null);
    }

    private void setUpRepositories() {
        when(userRepository.existsByEmail(any()))
                .thenAnswer(invocation -> usersByEmail.containsKey(invocation.getArgument(0)));
        when(userRepository.findByEmail(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        when(userRepository.existsById(any()))
                .thenAnswer(invocation -> usersById.containsKey(invocation.getArgument(0)));
        when(userRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            return streamIds(ids)
                    .map(usersById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        });
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
            com.synchat.backend.device.entity.DeviceEntity device = invocation.getArgument(0);
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

    private java.util.stream.Stream<UUID> streamIds(Iterable<UUID> ids) {
        return ids instanceof Collection<UUID> collection
                ? collection.stream()
                : java.util.stream.StreamSupport.stream(ids.spliterator(), false);
    }

    private LoginFixture registerAndLogin(String prefix) {
        String email = "presence." + prefix + "." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", prefix + " User"));
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest(email, "password123", deviceId, "android"));
        return new LoginFixture(login.user().id(), login, deviceId);
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

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private String url(String path) {
        return "ws://localhost:" + port + path;
    }

    private record LoginFixture(UUID userId, LoginResponse login, UUID deviceId) {
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
            JsonNode message = pollJsonMessage(objectMapper, TIMEOUT);
            assertThat(message).isNotNull();
            return message;
        }

        JsonNode pollJsonMessage(ObjectMapper objectMapper, Duration timeout) throws Exception {
            String message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return message == null ? null : objectMapper.readTree(message);
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
