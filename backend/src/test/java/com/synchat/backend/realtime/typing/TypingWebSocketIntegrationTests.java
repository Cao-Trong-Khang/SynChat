package com.synchat.backend.realtime.typing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.notification.NotificationDispatcher;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import com.synchat.backend.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
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
class TypingWebSocketIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(7);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(500);

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

    @MockBean
    private PresenceService presenceService;

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
    private final Map<UUID, ConversationEntity> conversationsById = new HashMap<>();
    private final Map<ConversationMemberId, ConversationMemberEntity> membersById = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();
    private final Map<String, ExpiringValue> redisValues = new HashMap<>();
    private final List<WebSocketSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        devicesById.clear();
        sessionsById.clear();
        conversationsById.clear();
        membersById.clear();
        redisSets.clear();
        redisValues.clear();
        sessions.clear();

        setUpRedis();
        setUpAuthRepositories();
        setUpConversationRepositories();
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
    void subscribeThenStartSendsTypingStartedToPeerOnly() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler socketA = connect(fixture.userA());
        TestWebSocketHandler socketB = connect(fixture.userB());

        sendTypingSubscribe(socketB, "req-subscribe", fixture.conversation().getId());
        awaitUntil(() -> hasSubscribers(fixture.conversation().getId()));
        sendTypingStart(socketA, "req-start", fixture.conversation().getId());

        JsonNode event = socketB.nextJsonMessage(objectMapper);
        assertTypingEvent(event, "typing.started", fixture.conversation().getId(), fixture.userA().user().id());
        assertThat(socketA.pollJsonMessage(objectMapper, SHORT_TIMEOUT)).isNull();
        assertNoPersistedEvents();
    }

    @Test
    void stopSendsTypingStoppedToPeer() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler socketA = connect(fixture.userA());
        TestWebSocketHandler socketB = connect(fixture.userB());

        sendTypingSubscribe(socketB, "req-subscribe", fixture.conversation().getId());
        awaitUntil(() -> hasSubscribers(fixture.conversation().getId()));
        sendTypingStart(socketA, "req-start", fixture.conversation().getId());
        socketB.nextJsonMessage(objectMapper);
        sendTypingStop(socketA, "req-stop", fixture.conversation().getId());

        JsonNode event = socketB.nextJsonMessage(objectMapper);
        assertTypingEvent(event, "typing.stopped", fixture.conversation().getId(), fixture.userA().user().id());
        assertNoPersistedEvents();
    }

    @Test
    void ttlExpirationSendsTypingStoppedToPeer() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler socketA = connect(fixture.userA());
        TestWebSocketHandler socketB = connect(fixture.userB());

        sendTypingSubscribe(socketB, "req-subscribe", fixture.conversation().getId());
        awaitUntil(() -> hasSubscribers(fixture.conversation().getId()));
        sendTypingStart(socketA, "req-start", fixture.conversation().getId());
        socketB.nextJsonMessage(objectMapper);

        JsonNode event = socketB.nextJsonMessage(objectMapper);
        assertTypingEvent(event, "typing.stopped", fixture.conversation().getId(), fixture.userA().user().id());
        assertNoPersistedEvents();
    }

    @Test
    void unsubscribeStopsTypingStartedDelivery() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler socketA = connect(fixture.userA());
        TestWebSocketHandler socketB = connect(fixture.userB());

        sendTypingSubscribe(socketB, "req-subscribe", fixture.conversation().getId());
        awaitUntil(() -> hasSubscribers(fixture.conversation().getId()));
        socketB.session().sendMessage(new TextMessage("""
                {
                  "type": "typing.unsubscribe",
                  "requestId": "req-unsubscribe",
                  "payload": {
                    "conversationId": "%s"
                  }
                }
                """.formatted(fixture.conversation().getId())));
        awaitUntil(() -> !hasSubscribers(fixture.conversation().getId()));
        sendTypingStart(socketA, "req-start", fixture.conversation().getId());

        assertThat(socketB.pollJsonMessage(objectMapper, SHORT_TIMEOUT)).isNull();
        assertNoPersistedEvents();
    }

    @Test
    void invalidStartPayloadReturnsValidationErrorEnvelope() throws Exception {
        LoginFixture userA = registerAndLogin("a");
        TestWebSocketHandler socketA = connect(userA);

        socketA.session().sendMessage(new TextMessage("""
                {
                  "type": "typing.start",
                  "requestId": "req-invalid",
                  "payload": {}
                }
                """));

        JsonNode response = socketA.nextJsonMessage(objectMapper);
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo("req-invalid");
        assertThat(response.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertNoPersistedEvents();
    }

    @Test
    void nonMemberTypingStartReturnsNotFoundEnvelope() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        LoginFixture userC = registerAndLogin("c");
        TestWebSocketHandler socketC = connect(userC);

        sendTypingStart(socketC, "req-non-member", fixture.conversation().getId());

        JsonNode response = socketC.nextJsonMessage(objectMapper);
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo("req-non-member");
        assertThat(response.path("error").path("code").asText()).isEqualTo("NOT_FOUND");
        assertNoPersistedEvents();
    }

    private void sendTypingSubscribe(TestWebSocketHandler socket, String requestId, UUID conversationId) throws Exception {
        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "typing.subscribe",
                  "requestId": "%s",
                  "payload": {
                    "conversationId": "%s"
                  }
                }
                """.formatted(requestId, conversationId)));
    }

    private void sendTypingStart(TestWebSocketHandler socket, String requestId, UUID conversationId) throws Exception {
        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "typing.start",
                  "requestId": "%s",
                  "payload": {
                    "conversationId": "%s"
                  }
                }
                """.formatted(requestId, conversationId)));
    }

    private void sendTypingStop(TestWebSocketHandler socket, String requestId, UUID conversationId) throws Exception {
        socket.session().sendMessage(new TextMessage("""
                {
                  "type": "typing.stop",
                  "requestId": "%s",
                  "payload": {
                    "conversationId": "%s"
                  }
                }
                """.formatted(requestId, conversationId)));
    }

    private void assertTypingEvent(JsonNode event, String type, UUID conversationId, UUID userId) {
        assertThat(event.path("type").asText()).isEqualTo(type);
        assertThat(event.at("/payload/conversationId").asText()).isEqualTo(conversationId.toString());
        assertThat(event.at("/payload/userId").asText()).isEqualTo(userId.toString());
    }

    private ConversationFixture createConversationFixture() {
        LoginFixture userA = registerAndLogin("a");
        LoginFixture userB = registerAndLogin("b");
        Instant now = Instant.now();
        ConversationEntity conversation = ConversationEntity.create(
                UUID.randomUUID(),
                "direct:test:" + UUID.randomUUID(),
                now
        );
        conversationsById.put(conversation.getId(), conversation);
        saveMember(ConversationMemberEntity.create(conversation.getId(), userA.user().id(), now));
        saveMember(ConversationMemberEntity.create(conversation.getId(), userB.user().id(), now));
        return new ConversationFixture(userA, userB, conversation);
    }

    private LoginFixture registerAndLogin(String prefix) {
        TestUser user = registerUser(prefix);
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest(user.email(), "password123", deviceId, "android"));
        return new LoginFixture(user, login, deviceId);
    }

    private TestUser registerUser(String prefix) {
        String email = "typing.ws." + prefix + "." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", prefix + " User"));
        return new TestUser(usersByEmail.get(email).getId(), email);
    }

    private void saveMember(ConversationMemberEntity member) {
        membersById.put(new ConversationMemberId(member.getConversationId(), member.getUserId()), member);
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

    private String url(String path) {
        return "ws://localhost:" + port + path;
    }

    private boolean hasSubscribers(UUID conversationId) {
        Set<String> socketIds = redisSets.get("typing_subscribers:" + conversationId);
        return socketIds != null && !socketIds.isEmpty();
    }

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private void setUpRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            Duration ttl = invocation.getArgument(2);
            redisValues.put(key, new ExpiringValue(value, System.nanoTime() + ttl.toNanos()));
            return null;
        }).when(valueOperations).set(any(String.class), any(String.class), any(Duration.class));
        when(redisTemplate.hasKey(any(String.class))).thenAnswer(invocation -> hasUnexpiredValue(invocation.getArgument(0)));
        when(redisTemplate.delete(any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean removedValue = redisValues.remove(key) != null;
            boolean removedSet = redisSets.remove(key) != null;
            return removedValue || removedSet;
        });

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
            Set<String> values = redisSets.get(invocation.getArgument(0));
            return values == null ? null : new HashSet<>(values);
        });
        when(setOperations.size(any())).thenAnswer(invocation -> {
            Set<String> values = redisSets.get(invocation.getArgument(0));
            return values == null ? 0L : (long) values.size();
        });
    }

    private boolean hasUnexpiredValue(String key) {
        ExpiringValue value = redisValues.get(key);
        if (value == null) {
            return false;
        }
        if (System.nanoTime() >= value.expiresAtNanos()) {
            redisValues.remove(key);
            return false;
        }
        return true;
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

    private void setUpConversationRepositories() {
        when(conversationMemberRepository.findByConversationIdAndUserId(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            return Optional.ofNullable(membersById.get(new ConversationMemberId(conversationId, userId)));
        });
    }

    private java.util.stream.Stream<UUID> streamIds(Iterable<UUID> ids) {
        return ids instanceof Collection<UUID> collection
                ? collection.stream()
                : java.util.stream.StreamSupport.stream(ids.spliterator(), false);
    }

    private void assertNoPersistedEvents() {
        verify(userEventRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private record TestUser(UUID id, String email) {
    }

    private record LoginFixture(TestUser user, LoginResponse login, UUID deviceId) {
    }

    private record ConversationFixture(LoginFixture userA, LoginFixture userB, ConversationEntity conversation) {
    }

    private record ExpiringValue(String value, long expiresAtNanos) {
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
