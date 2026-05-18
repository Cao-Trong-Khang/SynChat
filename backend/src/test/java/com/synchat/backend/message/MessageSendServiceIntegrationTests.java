package com.synchat.backend.message;

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
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.dto.SendMessageCommandPayload;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
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
class MessageSendServiceIntegrationTests {

    @MockBean
    private TypingService typingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MessageSendService messageSendService;

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
    private final Map<String, ConversationEntity> conversationsByDirectKey = new HashMap<>();
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
        conversationsByDirectKey.clear();
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
    void sendingFirstMessageToRecipientCreatesDirectConversation() {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        setCurrentUser(sender.id());
        UUID clientMessageId = UUID.randomUUID();

        messageSendService.send(new SendMessageCommandPayload(null, recipient.id(), clientMessageId, "hello"));

        String directKey = DirectConversationKeyUtil.build(sender.id(), recipient.id());
        ConversationEntity conversation = conversationsByDirectKey.get(directKey);
        assertThat(conversation).isNotNull();
        assertThat(conversationsByDirectKey).containsOnlyKeys(directKey);
        assertThat(membersByConversation(conversation.getId())).hasSize(2);
        assertThat(messagesById).hasSize(1);

        MessageEntity message = onlyMessage();
        assertThat(message.getSeq()).isEqualTo(1L);
        assertThat(message.getSenderId()).isEqualTo(sender.id());
        assertThat(message.getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(conversation.getLastMessageSeq()).isEqualTo(1L);
        assertThat(conversation.getLastMessageAt()).isNotNull();
        assertThat(member(conversation.getId(), recipient.id()).getUnreadCount()).isEqualTo(1);
        assertThat(member(conversation.getId(), sender.id()).getUnreadCount()).isZero();
        assertThat(userEventsFor(sender.id())).hasSize(1);
        assertThat(userEventsFor(recipient.id())).hasSize(1);
        assertThat(outboxEvents).hasSize(2);
    }

    @Test
    void sendingSecondMessageInExistingConversationIncrementsSequence() {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        setCurrentUser(sender.id());
        messageSendService.send(new SendMessageCommandPayload(null, recipient.id(), UUID.randomUUID(), "one"));
        ConversationEntity conversation = conversationsByDirectKey.get(
                DirectConversationKeyUtil.build(sender.id(), recipient.id())
        );

        messageSendService.send(new SendMessageCommandPayload(conversation.getId(), null, UUID.randomUUID(), "two"));

        assertThat(messagesById.values())
                .extracting(MessageEntity::getSeq)
                .containsExactlyInAnyOrder(1L, 2L);
        MessageEntity second = messagesById.values()
                .stream()
                .filter(message -> message.getSeq().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(second.getBody()).isEqualTo("two");
        assertThat(conversation.getLastMessageSeq()).isEqualTo(2L);
        assertThat(member(conversation.getId(), recipient.id()).getUnreadCount()).isEqualTo(2);
    }

    @Test
    void retryWithSameSenderAndClientMessageIdDoesNotDuplicateMessage() {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        setCurrentUser(sender.id());
        UUID clientMessageId = UUID.randomUUID();
        SendMessageCommandPayload payload = new SendMessageCommandPayload(
                null,
                recipient.id(),
                clientMessageId,
                "hello"
        );

        messageSendService.send(payload);
        ConversationEntity conversation = conversationsByDirectKey.get(
                DirectConversationKeyUtil.build(sender.id(), recipient.id())
        );
        messageSendService.send(payload);

        assertThat(messagesById).hasSize(1);
        assertThat(conversation.getLastMessageSeq()).isEqualTo(1L);
        assertThat(member(conversation.getId(), recipient.id()).getUnreadCount()).isEqualTo(1);
        assertThat(userEventsFor(sender.id())).hasSize(2);
        assertThat(userEventsFor(recipient.id())).hasSize(1);
        assertThat(outboxFor(sender.id())).hasSize(2);
        assertThat(outboxFor(recipient.id())).hasSize(1);
    }

    @Test
    void existingConversationUnauthorizedReturnsNotFound() {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        TestUser third = registerUser("third");
        setCurrentUser(sender.id());
        messageSendService.send(new SendMessageCommandPayload(null, recipient.id(), UUID.randomUUID(), "hello"));
        ConversationEntity conversation = conversationsByDirectKey.get(
                DirectConversationKeyUtil.build(sender.id(), recipient.id())
        );
        setCurrentUser(third.id());

        assertThatThrownBy(() -> messageSendService.send(new SendMessageCommandPayload(
                conversation.getId(),
                null,
                UUID.randomUUID(),
                "not allowed"
        )))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void invalidPayloadThrowsAppException() {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        setCurrentUser(sender.id());

        assertAppException(
                new SendMessageCommandPayload(null, null, UUID.randomUUID(), "hello"),
                ErrorCode.VALIDATION_ERROR
        );
        assertAppException(
                new SendMessageCommandPayload(UUID.randomUUID(), recipient.id(), UUID.randomUUID(), "hello"),
                ErrorCode.VALIDATION_ERROR
        );
        assertAppException(
                new SendMessageCommandPayload(null, recipient.id(), null, "hello"),
                ErrorCode.VALIDATION_ERROR
        );
        assertAppException(
                new SendMessageCommandPayload(null, recipient.id(), UUID.randomUUID(), "   "),
                ErrorCode.VALIDATION_ERROR
        );
        assertAppException(
                new SendMessageCommandPayload(null, recipient.id(), UUID.randomUUID(), "x".repeat(4001)),
                ErrorCode.MESSAGE_TOO_LARGE
        );
    }

    @Test
    void eventPayloadIsPersonalized() throws Exception {
        TestUser sender = registerUser("sender");
        TestUser recipient = registerUser("recipient");
        setCurrentUser(sender.id());

        messageSendService.send(new SendMessageCommandPayload(null, recipient.id(), UUID.randomUUID(), "hello"));

        JsonNode senderPayload = objectMapper.readTree(userEventsFor(sender.id()).get(0).getPayload());
        JsonNode recipientPayload = objectMapper.readTree(userEventsFor(recipient.id()).get(0).getPayload());

        assertThat(senderPayload.at("/conversation/peer/id").asText()).isEqualTo(recipient.id().toString());
        assertThat(senderPayload.at("/conversation/unreadCount").asInt()).isZero();
        assertThat(recipientPayload.at("/conversation/peer/id").asText()).isEqualTo(sender.id().toString());
        assertThat(recipientPayload.at("/conversation/unreadCount").asInt()).isEqualTo(1);

        JsonNode senderOutbox = objectMapper.readTree(outboxFor(sender.id()).get(0).getPayload());
        assertThat(senderOutbox.path("eventType").asText()).isEqualTo("message.created");
        assertThat(senderOutbox.path("payload").at("/conversation/peer/id").asText())
                .isEqualTo(recipient.id().toString());
    }

    private void assertAppException(SendMessageCommandPayload payload, ErrorCode expectedCode) {
        assertThatThrownBy(() -> messageSendService.send(payload))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
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
        when(conversationRepository.findByDirectKeyForUpdate(any())).thenAnswer(
                invocation -> Optional.ofNullable(conversationsByDirectKey.get(invocation.getArgument(0))));
        when(conversationRepository.save(any())).thenAnswer(invocation -> saveConversation(invocation.getArgument(0)));
        when(conversationRepository.saveAndFlush(any())).thenAnswer(
                invocation -> saveConversation(invocation.getArgument(0)));

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
        when(conversationMemberRepository.findByConversationId(any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            return membersByConversation(conversationId);
        });
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

    private ConversationEntity saveConversation(ConversationEntity conversation) {
        conversationsById.put(conversation.getId(), conversation);
        conversationsByDirectKey.put(conversation.getDirectKey(), conversation);
        return conversation;
    }

    private TestUser registerUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        String email = prefix + "." + suffix + "@example.com";
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

    private MessageEntity onlyMessage() {
        assertThat(messagesById).hasSize(1);
        return messagesById.values().iterator().next();
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
