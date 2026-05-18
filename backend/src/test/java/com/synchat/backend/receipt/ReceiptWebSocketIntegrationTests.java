package com.synchat.backend.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.notification.NotificationDispatcher;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.ClaimedOutboxEvent;
import com.synchat.backend.outbox.service.OutboxClaimService;
import com.synchat.backend.outbox.service.OutboxPublishService;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;
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
class ReceiptWebSocketIntegrationTests {

    @MockBean
    private TypingService typingService;

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxPublishService outboxPublishService;

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
    private final Map<UUID, ConversationEntity> conversationsById = new HashMap<>();
    private final Map<ConversationMemberId, ConversationMemberEntity> membersById = new HashMap<>();
    private final Map<UUID, MessageEntity> messagesById = new HashMap<>();
    private final Map<UserEventId, UserEventEntity> userEventsById = new HashMap<>();
    private final Map<UUID, OutboxEventEntity> outboxEventsById = new HashMap<>();
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
        messagesById.clear();
        userEventsById.clear();
        outboxEventsById.clear();
        sessions.clear();

        setUpRedis();
        setUpAuthRepositories();
        setUpConversationRepositories();
        setUpMessageRepositories();
        setUpEventRepositories();
        setUpOutboxRepository();
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
    void deliveredUpToPublishesDeliveredEventToSenderAndRecipientSockets() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler senderSocket = connect(fixture.sender());
        TestWebSocketHandler recipientSocket = connect(fixture.recipient());

        recipientSocket.session().sendMessage(new TextMessage("""
                {
                  "type": "delivered.up_to",
                  "requestId": "req-delivered-1",
                  "payload": {
                    "conversationId": "%s",
                    "deliveredSeq": 1
                  }
                }
                """.formatted(fixture.conversation().getId())));

        awaitUntil(() -> outboxEventsById.size() == 2);
        publishPendingOutbox();

        assertReceiptEvent(senderSocket.nextJsonMessage(objectMapper), "message.delivered", fixture.recipient(), 1L);
        assertReceiptEvent(recipientSocket.nextJsonMessage(objectMapper), "message.delivered", fixture.recipient(), 1L);
    }

    @Test
    void readUpToPublishesReadEventAndClearsUnreadCount() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        TestWebSocketHandler senderSocket = connect(fixture.sender());
        TestWebSocketHandler recipientSocket = connect(fixture.recipient());

        recipientSocket.session().sendMessage(new TextMessage("""
                {
                  "type": "read.up_to",
                  "requestId": "req-read-1",
                  "payload": {
                    "conversationId": "%s",
                    "readSeq": 1
                  }
                }
                """.formatted(fixture.conversation().getId())));

        awaitUntil(() -> outboxEventsById.size() == 2);
        publishPendingOutbox();

        assertReceiptEvent(senderSocket.nextJsonMessage(objectMapper), "message.read", fixture.recipient(), 1L);
        assertReceiptEvent(recipientSocket.nextJsonMessage(objectMapper), "message.read", fixture.recipient(), 1L);
        assertThat(member(fixture.conversation().getId(), fixture.recipient().user().id()).getUnreadCount()).isZero();
    }

    @Test
    void invalidReceiptCommandReturnsValidationErrorEnvelope() throws Exception {
        LoginFixture recipient = registerAndLogin("recipient");
        TestWebSocketHandler recipientSocket = connect(recipient);

        recipientSocket.session().sendMessage(new TextMessage("""
                {
                  "type": "delivered.up_to",
                  "requestId": "req-invalid",
                  "payload": {
                    "deliveredSeq": -1
                  }
                }
                """));

        JsonNode response = recipientSocket.nextJsonMessage(objectMapper);
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo("req-invalid");
        assertThat(response.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    private void assertReceiptEvent(JsonNode event, String eventType, LoginFixture actor, Long seq) {
        assertThat(event.path("type").asText()).isEqualTo("event");
        assertThat(event.path("eventType").asText()).isEqualTo(eventType);
        assertThat(event.at("/payload/userId").asText()).isEqualTo(actor.user().id().toString());
        if ("message.delivered".equals(eventType)) {
            assertThat(event.at("/payload/deliveredSeq").asLong()).isEqualTo(seq);
        } else {
            assertThat(event.at("/payload/readSeq").asLong()).isEqualTo(seq);
        }
    }

    private void publishPendingOutbox() {
        List<ClaimedOutboxEvent> claimed = outboxClaimService.claimPending(10);
        assertThat(claimed).hasSize(2);
        claimed.forEach(outboxPublishService::publish);
    }

    private ConversationFixture createConversationFixture() {
        LoginFixture sender = registerAndLogin("sender");
        LoginFixture recipient = registerAndLogin("recipient");
        Instant now = Instant.now();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = ConversationEntity.create(
                conversationId,
                DirectConversationKeyUtil.build(sender.user().id(), recipient.user().id()),
                now
        );
        conversation.applyLastMessage(1L, now);
        conversationsById.put(conversationId, conversation);

        saveMember(ConversationMemberEntity.create(conversationId, sender.user().id(), now));
        saveMember(new ConversationMemberEntity(conversationId, recipient.user().id(), 0L, 0L, 1, now, now));
        MessageEntity message = MessageEntity.create(
                UUID.randomUUID(),
                conversationId,
                1L,
                sender.user().id(),
                UUID.randomUUID(),
                "hello",
                now
        );
        messagesById.put(message.getId(), message);
        return new ConversationFixture(sender, recipient, conversation);
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
        when(conversationRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> Optional.ofNullable(conversationsById.get(invocation.getArgument(0))));

        when(conversationMemberRepository.save(any()))
                .thenAnswer(invocation -> saveMember(invocation.getArgument(0)));
        when(conversationMemberRepository.findByConversationIdAndUserId(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            return Optional.ofNullable(member(conversationId, userId));
        });
        when(conversationMemberRepository.findByConversationIdAndUserIdForUpdate(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            return Optional.ofNullable(member(conversationId, userId));
        });
        when(conversationMemberRepository.findByConversationId(any()))
                .thenAnswer(invocation -> membersByConversation(invocation.getArgument(0)));
    }

    private void setUpMessageRepositories() {
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            messagesById.put(message.getId(), message);
            return message;
        });
    }

    private void setUpEventRepositories() {
        when(userEventOffsetRepository.findByUserIdForUpdate(any()))
                .thenAnswer(invocation -> Optional.ofNullable(offsetsByUserId.get(invocation.getArgument(0))));
        when(userEventOffsetRepository.save(any())).thenAnswer(invocation -> {
            UserEventOffsetEntity offset = invocation.getArgument(0);
            offsetsByUserId.put(offset.getUserId(), offset);
            return offset;
        });
        when(userEventRepository.save(any())).thenAnswer(invocation -> {
            UserEventEntity event = invocation.getArgument(0);
            userEventsById.put(new UserEventId(event.getUserId(), event.getEventSeq()), event);
            return event;
        });
    }

    private void setUpOutboxRepository() {
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> {
            OutboxEventEntity event = invocation.getArgument(0);
            outboxEventsById.put(event.getId(), event);
            return event;
        });
        when(outboxEventRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(outboxEventsById.get(invocation.getArgument(0))));
        when(outboxEventRepository.findByStatusForUpdate(any(), any())).thenAnswer(invocation -> {
            String status = invocation.getArgument(0);
            Pageable pageable = invocation.getArgument(1);
            return outboxEventsById.values()
                    .stream()
                    .filter(event -> event.getStatus().equals(status))
                    .sorted(Comparator.comparing(OutboxEventEntity::getCreatedAt))
                    .limit(pageable.getPageSize())
                    .toList();
        });
    }

    private ConversationMemberEntity saveMember(ConversationMemberEntity member) {
        membersById.put(new ConversationMemberId(member.getConversationId(), member.getUserId()), member);
        return member;
    }

    private ConversationMemberEntity member(UUID conversationId, UUID userId) {
        return membersById.get(new ConversationMemberId(conversationId, userId));
    }

    private List<ConversationMemberEntity> membersByConversation(UUID conversationId) {
        return membersById.values()
                .stream()
                .filter(member -> member.getConversationId().equals(conversationId))
                .toList();
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

    private LoginFixture registerAndLogin(String prefix) {
        TestUser user = registerUser(prefix);
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest(user.email(), "password123", deviceId, "ios"));
        return new LoginFixture(user, login, deviceId);
    }

    private TestUser registerUser(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", prefix + " User"));
        return new TestUser(usersByEmail.get(email).getId(), email);
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

    private record TestUser(UUID id, String email) {
    }

    private record LoginFixture(TestUser user, LoginResponse login, UUID deviceId) {
    }

    private record ConversationFixture(LoginFixture sender, LoginFixture recipient, ConversationEntity conversation) {
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
