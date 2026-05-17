package com.synchat.backend.message;

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
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
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
class SendMessageWebSocketIntegrationTests {

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
    private final Map<String, ConversationEntity> conversationsByDirectKey = new HashMap<>();
    private final Map<ConversationMemberId, ConversationMemberEntity> membersById = new HashMap<>();
    private final Map<UUID, MessageEntity> messagesById = new HashMap<>();
    private final Map<UserEventId, UserEventEntity> userEventsById = new HashMap<>();
    private final List<OutboxEventEntity> outboxEvents = new ArrayList<>();
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
        conversationsByDirectKey.clear();
        membersById.clear();
        messagesById.clear();
        userEventsById.clear();
        outboxEvents.clear();
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
    void sendVirtualConversationMessagePersistsMessageEventsAndOutboxRows() throws Exception {
        LoginFixture sender = registerAndLogin("sender");
        TestUser recipient = registerUser("recipient");
        UUID clientMessageId = UUID.randomUUID();
        TestWebSocketHandler handler = connect(sender);

        handler.session().sendMessage(new TextMessage("""
                {
                  "type": "messages.send",
                  "requestId": "req-send-1",
                  "payload": {
                    "recipientId": "%s",
                    "clientMessageId": "%s",
                    "body": "hello"
                  }
                }
                """.formatted(recipient.id(), clientMessageId)));

        awaitUntil(() -> messagesById.size() == 1 && userEventsById.size() == 2 && outboxEvents.size() == 2);

        String directKey = DirectConversationKeyUtil.build(sender.user().id(), recipient.id());
        ConversationEntity conversation = conversationsByDirectKey.get(directKey);
        assertThat(conversation).isNotNull();
        assertThat(membersByConversation(conversation.getId())).hasSize(2);

        MessageEntity message = messagesById.values().iterator().next();
        assertThat(message.getConversationId()).isEqualTo(conversation.getId());
        assertThat(message.getSenderId()).isEqualTo(sender.user().id());
        assertThat(message.getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(message.getSeq()).isEqualTo(1L);
        assertThat(message.getBody()).isEqualTo("hello");
        assertThat(userEventsById).hasSize(2);
        assertThat(outboxEvents).hasSize(2);
    }

    @Test
    void sendInvalidCommandPayloadReturnsErrorEnvelope() throws Exception {
        LoginFixture sender = registerAndLogin("sender");
        TestUser recipient = registerUser("recipient");
        TestWebSocketHandler handler = connect(sender);

        handler.session().sendMessage(new TextMessage("""
                {
                  "type": "messages.send",
                  "requestId": "req-invalid",
                  "payload": {
                    "recipientId": "%s",
                    "body": "hello"
                  }
                }
                """.formatted(recipient.id())));

        JsonNode response = handler.nextJsonMessage(objectMapper);
        assertThat(response.path("type").asText()).isEqualTo("error");
        assertThat(response.path("requestId").asText()).isEqualTo("req-invalid");
        assertThat(response.path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void retrySameClientMessageIdDoesNotInsertDuplicateMessage() throws Exception {
        LoginFixture sender = registerAndLogin("sender");
        TestUser recipient = registerUser("recipient");
        UUID clientMessageId = UUID.randomUUID();
        TestWebSocketHandler handler = connect(sender);
        TextMessage command = new TextMessage("""
                {
                  "type": "messages.send",
                  "requestId": "req-retry",
                  "payload": {
                    "recipientId": "%s",
                    "clientMessageId": "%s",
                    "body": "hello"
                  }
                }
                """.formatted(recipient.id(), clientMessageId));

        handler.session().sendMessage(command);
        handler.session().sendMessage(command);

        awaitUntil(() -> userEventsById.size() == 3 && outboxEvents.size() == 3);

        assertThat(messagesById).hasSize(1);
        assertThat(messagesById.values().iterator().next().getClientMessageId()).isEqualTo(clientMessageId);
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

        when(userCredentialRepository.findById(any())).thenAnswer(
                invocation -> Optional.ofNullable(credentialsByUserId.get(invocation.getArgument(0))));
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
        when(conversationRepository.findByDirectKeyForUpdate(any()))
                .thenAnswer(invocation -> Optional.ofNullable(conversationsByDirectKey.get(invocation.getArgument(0))));
        when(conversationRepository.save(any())).thenAnswer(invocation -> saveConversation(invocation.getArgument(0)));
        when(conversationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> saveConversation(invocation.getArgument(0)));

        when(conversationMemberRepository.save(any())).thenAnswer(invocation -> {
            ConversationMemberEntity member = invocation.getArgument(0);
            membersById.put(new ConversationMemberId(member.getConversationId(), member.getUserId()), member);
            return member;
        });
        when(conversationMemberRepository.findByConversationIdAndUserId(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            return Optional.ofNullable(membersById.get(new ConversationMemberId(conversationId, userId)));
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
        when(messageRepository.findBySenderIdAndClientMessageId(any(), any())).thenAnswer(invocation -> {
            UUID senderId = invocation.getArgument(0);
            UUID clientMessageId = invocation.getArgument(1);
            return messagesById.values()
                    .stream()
                    .filter(message -> message.getSenderId().equals(senderId))
                    .filter(message -> message.getClientMessageId().equals(clientMessageId))
                    .findFirst();
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
            outboxEvents.add(event);
            return event;
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

    private ConversationEntity saveConversation(ConversationEntity conversation) {
        conversationsById.put(conversation.getId(), conversation);
        conversationsByDirectKey.put(conversation.getDirectKey(), conversation);
        return conversation;
    }

    private List<ConversationMemberEntity> membersByConversation(UUID conversationId) {
        return membersById.values()
                .stream()
                .filter(member -> member.getConversationId().equals(conversationId))
                .toList();
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
