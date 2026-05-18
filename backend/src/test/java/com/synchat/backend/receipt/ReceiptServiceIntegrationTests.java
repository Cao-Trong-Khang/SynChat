package com.synchat.backend.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.receipt.dto.DeliveredUpToCommandPayload;
import com.synchat.backend.receipt.dto.ReadUpToCommandPayload;
import com.synchat.backend.receipt.model.ReceiptEventTypes;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.synchat.backend.realtime.typing.service.TypingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class ReceiptServiceIntegrationTests {

    @MockBean
    private TypingService typingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReceiptService receiptService;

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
    private final List<OutboxEventEntity> outboxEvents = new ArrayList<>();

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
        outboxEvents.clear();

        setUpAuthRepositories();
        setUpConversationRepositories();
        setUpMessageRepositories();
        setUpEventRepositories();
        setUpOutboxRepository();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deliveredUpToUpdatesMemberAndCreatesEventsForBothUsers() throws Exception {
        ConversationFixture fixture = createConversationFixture(5);
        setCurrentUser(fixture.recipient().id());

        receiptService.deliveredUpTo(new DeliveredUpToCommandPayload(fixture.conversation().getId(), 3L));

        ConversationMemberEntity currentMember = member(fixture.conversation().getId(), fixture.recipient().id());
        assertThat(currentMember.getDeliveredSeq()).isEqualTo(3L);
        assertThat(userEventsFor(fixture.recipient().id())).hasSize(1);
        assertThat(userEventsFor(fixture.sender().id())).hasSize(1);
        assertThat(outboxEvents).hasSize(2);
        assertReceiptEvent(userEventsFor(fixture.recipient().id()).get(0), ReceiptEventTypes.MESSAGE_DELIVERED);
        assertReceiptEvent(userEventsFor(fixture.sender().id()).get(0), ReceiptEventTypes.MESSAGE_DELIVERED);

        JsonNode payload = objectMapper.readTree(userEventsFor(fixture.recipient().id()).get(0).getPayload());
        assertThat(payload.path("userId").asText()).isEqualTo(fixture.recipient().id().toString());
        assertThat(payload.path("deliveredSeq").asLong()).isEqualTo(3L);
    }

    @Test
    void deliveredUpToStaleSeqDoesNotCreateEvents() {
        ConversationFixture fixture = createConversationFixture(5);
        setCurrentUser(fixture.recipient().id());
        member(fixture.conversation().getId(), fixture.recipient().id()).updateDeliveredSeq(4L, Instant.now());

        receiptService.deliveredUpTo(new DeliveredUpToCommandPayload(fixture.conversation().getId(), 2L));

        assertThat(member(fixture.conversation().getId(), fixture.recipient().id()).getDeliveredSeq()).isEqualTo(4L);
        assertThat(userEventsById).isEmpty();
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    void readUpToUpdatesReadSeqClearsUnreadAndCreatesEventsForBothUsers() throws Exception {
        ConversationFixture fixture = createConversationFixture(5);
        setCurrentUser(fixture.recipient().id());

        receiptService.readUpTo(new ReadUpToCommandPayload(fixture.conversation().getId(), 5L));

        ConversationMemberEntity currentMember = member(fixture.conversation().getId(), fixture.recipient().id());
        assertThat(currentMember.getReadSeq()).isEqualTo(5L);
        assertThat(currentMember.getUnreadCount()).isZero();
        assertThat(userEventsFor(fixture.recipient().id())).hasSize(1);
        assertThat(userEventsFor(fixture.sender().id())).hasSize(1);
        assertThat(outboxEvents).hasSize(2);
        assertReceiptEvent(userEventsFor(fixture.recipient().id()).get(0), ReceiptEventTypes.MESSAGE_READ);
        assertReceiptEvent(userEventsFor(fixture.sender().id()).get(0), ReceiptEventTypes.MESSAGE_READ);

        JsonNode payload = objectMapper.readTree(userEventsFor(fixture.recipient().id()).get(0).getPayload());
        assertThat(payload.path("userId").asText()).isEqualTo(fixture.recipient().id().toString());
        assertThat(payload.path("readSeq").asLong()).isEqualTo(5L);
    }

    @Test
    void readUpToCapsSeqToLastMessageSeq() {
        ConversationFixture fixture = createConversationFixture(5);
        setCurrentUser(fixture.recipient().id());

        receiptService.readUpTo(new ReadUpToCommandPayload(fixture.conversation().getId(), 100L));

        assertThat(member(fixture.conversation().getId(), fixture.recipient().id()).getReadSeq()).isEqualTo(5L);
    }

    @Test
    void nonMemberCannotUpdateReceipt() {
        ConversationFixture fixture = createConversationFixture(5);
        TestUser third = registerUser("third");
        setCurrentUser(third.id());

        assertThatThrownBy(() -> receiptService.readUpTo(new ReadUpToCommandPayload(
                fixture.conversation().getId(),
                1L
        )))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void invalidPayloadThrowsValidationError() {
        ConversationFixture fixture = createConversationFixture(5);
        setCurrentUser(fixture.recipient().id());

        assertAppException(new DeliveredUpToCommandPayload(null, 1L), ErrorCode.VALIDATION_ERROR);
        assertAppException(new DeliveredUpToCommandPayload(fixture.conversation().getId(), -1L),
                ErrorCode.VALIDATION_ERROR);
        assertAppException(new ReadUpToCommandPayload(null, 1L), ErrorCode.VALIDATION_ERROR);
        assertAppException(new ReadUpToCommandPayload(fixture.conversation().getId(), -1L),
                ErrorCode.VALIDATION_ERROR);
    }

    private void assertAppException(DeliveredUpToCommandPayload payload, ErrorCode expectedCode) {
        assertThatThrownBy(() -> receiptService.deliveredUpTo(payload))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
    }

    private void assertAppException(ReadUpToCommandPayload payload, ErrorCode expectedCode) {
        assertThatThrownBy(() -> receiptService.readUpTo(payload))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
    }

    private void assertReceiptEvent(UserEventEntity event, String eventType) throws Exception {
        assertThat(event.getEventType()).isEqualTo(eventType);
        JsonNode outboxPayload = objectMapper.readTree(outboxFor(event.getUserId()).get(0).getPayload());
        assertThat(outboxPayload.path("userId").asText()).isEqualTo(event.getUserId().toString());
        assertThat(outboxPayload.path("eventSeq").asLong()).isEqualTo(event.getEventSeq());
        assertThat(outboxPayload.path("eventType").asText()).isEqualTo(eventType);
    }

    private ConversationFixture createConversationFixture(int lastMessageSeq) {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        Instant now = Instant.now();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = ConversationEntity.create(conversationId, "direct:%s:%s".formatted(
                sender.id(),
                recipient.id()
        ), now);
        conversation.applyLastMessage((long) lastMessageSeq, now);
        saveConversation(conversation);

        saveMember(ConversationMemberEntity.create(conversationId, sender.id(), now));
        ConversationMemberEntity recipientMember = new ConversationMemberEntity(
                conversationId,
                recipient.id(),
                0L,
                0L,
                5,
                now,
                now
        );
        saveMember(recipientMember);

        for (long seq = 1; seq <= lastMessageSeq; seq++) {
            MessageEntity message = MessageEntity.create(
                    UUID.randomUUID(),
                    conversationId,
                    seq,
                    sender.id(),
                    UUID.randomUUID(),
                    "message-" + seq,
                    now.plusMillis(seq)
            );
            messagesById.put(message.getId(), message);
        }

        return new ConversationFixture(sender, recipient, conversation);
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
        when(conversationRepository.save(any())).thenAnswer(invocation -> saveConversation(invocation.getArgument(0)));

        when(conversationMemberRepository.save(any())).thenAnswer(invocation -> {
            ConversationMemberEntity member = invocation.getArgument(0);
            return saveMember(member);
        });
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
            outboxEvents.add(event);
            return event;
        });
    }

    private TestUser registerUser(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", prefix + " User"));
        return new TestUser(usersByEmail.get(email).getId(), email);
    }

    private void setCurrentUser(UUID userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, UUID.randomUUID(), UUID.randomUUID());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ConversationEntity saveConversation(ConversationEntity conversation) {
        conversationsById.put(conversation.getId(), conversation);
        return conversation;
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

    private List<UserEventEntity> userEventsFor(UUID userId) {
        return userEventsById.values()
                .stream()
                .filter(event -> event.getUserId().equals(userId))
                .sorted((left, right) -> left.getEventSeq().compareTo(right.getEventSeq()))
                .toList();
    }

    private List<OutboxEventEntity> outboxFor(UUID userId) {
        return outboxEvents.stream()
                .filter(event -> {
                    try {
                        return objectMapper.readTree(event.getPayload()).path("userId").asText().equals(userId.toString());
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
    }

    private record TestUser(UUID id, String email) {
    }

    private record ConversationFixture(TestUser sender, TestUser recipient, ConversationEntity conversation) {
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
