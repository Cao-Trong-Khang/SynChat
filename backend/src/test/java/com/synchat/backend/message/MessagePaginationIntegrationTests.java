package com.synchat.backend.message;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ActiveProfiles("test")
@AutoConfigureMockMvc
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
class MessagePaginationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

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
    private ConversationRepository conversationRepository;

    @MockBean
    private ConversationMemberRepository conversationMemberRepository;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private ConversationMetadataMapper conversationMetadataMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private SyncService syncService;

    @MockBean
    private MessageSendService messageSendService;

    @MockBean
    private OutboxEventAppender outboxEventAppender;

    @MockBean
    private UserEventAppender userEventAppender;

    private final Map<UUID, UserEntity> usersById = new HashMap<>();
    private final Map<String, UserEntity> usersByEmail = new HashMap<>();
    private final Map<UUID, UserCredentialEntity> credentialsByUserId = new HashMap<>();
    private final Map<UUID, UserEventOffsetEntity> offsetsByUserId = new HashMap<>();
    private final Map<UUID, DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();
    private final Map<UUID, ConversationEntity> conversationsById = new HashMap<>();
    private final Map<ConversationMemberId, ConversationMemberEntity> membersById = new HashMap<>();
    private final Map<UUID, MessageEntity> messagesById = new HashMap<>();

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

        setUpAuthRepositories();
        setUpConversationRepositories();
        setUpMessageRepositories();
    }

    @Test
    void latestMessagesWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/conversations/{conversationId}/messages", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void latestMessagesAsNonMemberReturnsNotFound() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        TestLogin nonMember = registerAndLogin("third");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(nonMember.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void latestMessagesWithLimitTwentyReturnsSeqSixThroughTwentyFive() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("limit", "20")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId", is(conversationId.toString())))
                .andExpect(jsonPath("$.messages", hasSize(20)))
                .andExpect(jsonPath("$.messages[*].seq", contains(
                        6, 7, 8, 9, 10,
                        11, 12, 13, 14, 15,
                        16, 17, 18, 19, 20,
                        21, 22, 23, 24, 25
                )))
                .andExpect(jsonPath("$.messages[0].senderId", is(currentUser.userId().toString())))
                .andExpect(jsonPath("$.messages[0].clientMessageId").exists())
                .andExpect(jsonPath("$.messages[0].body", is("message-6")))
                .andExpect(jsonPath("$.messages[0].createdAtServer").exists())
                .andExpect(jsonPath("$.oldestSeq", is(6)))
                .andExpect(jsonPath("$.newestSeq", is(25)))
                .andExpect(jsonPath("$.hasMoreBefore", is(true)));
    }

    @Test
    void olderMessagesBeforeSeqSixReturnsSeqOneThroughFive() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("beforeSeq", "6")
                .param("limit", "20")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(5)))
                .andExpect(jsonPath("$.messages[*].seq", contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.oldestSeq", is(1)))
                .andExpect(jsonPath("$.newestSeq").doesNotExist())
                .andExpect(jsonPath("$.hasMoreBefore", is(false)));
    }

    @Test
    void olderMessagesBeforeSeqTwentyOneWithLimitTenReturnsSeqElevenThroughTwenty() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("beforeSeq", "21")
                .param("limit", "10")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(10)))
                .andExpect(jsonPath("$.messages[*].seq", contains(11, 12, 13, 14, 15, 16, 17, 18, 19, 20)))
                .andExpect(jsonPath("$.oldestSeq", is(11)))
                .andExpect(jsonPath("$.newestSeq").doesNotExist())
                .andExpect(jsonPath("$.hasMoreBefore", is(true)));
    }

    @Test
    void invalidLimitReturnsValidationError() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("limit", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("limit", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void beforeSeqLessThanOrEqualToOneReturnsEmptyPage() throws Exception {
        TestLogin currentUser = registerAndLogin("current");
        TestLogin peer = registerAndLogin("peer");
        UUID conversationId = seedDirectConversation(currentUser.userId(), peer.userId());

        mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId)
                .param("beforeSeq", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(currentUser.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(0)))
                .andExpect(jsonPath("$.oldestSeq").doesNotExist())
                .andExpect(jsonPath("$.newestSeq").doesNotExist())
                .andExpect(jsonPath("$.hasMoreBefore", is(false)));
    }

    private void setUpAuthRepositories() {
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

        when(authSessionRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(sessionsById.get(invocation.getArgument(0))));
        when(authSessionRepository.findByRefreshTokenHashAndDeviceId(any(), any())).thenAnswer(invocation -> {
            String refreshTokenHash = invocation.getArgument(0);
            UUID deviceId = invocation.getArgument(1);
            return sessionsById.values()
                    .stream()
                    .filter(session -> session.getRefreshTokenHash().equals(refreshTokenHash))
                    .filter(session -> session.getDeviceId().equals(deviceId))
                    .findFirst();
        });
        when(authSessionRepository.save(any())).thenAnswer(invocation -> {
            AuthSessionEntity session = invocation.getArgument(0);
            sessionsById.put(session.getId(), session);
            return session;
        });
    }

    private void setUpConversationRepositories() {
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversationsById.put(conversation.getId(), conversation);
            return conversation;
        });

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
    }

    private void setUpMessageRepositories() {
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            messagesById.put(message.getId(), message);
            return message;
        });
        when(messageRepository.findLatestByConversationId(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            Pageable pageable = invocation.getArgument(1);
            return messagesByConversationDesc(conversationId, pageable);
        });
        when(messageRepository.findOlderByConversationId(any(), any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            Long beforeSeq = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(2);
            return messagesById.values()
                    .stream()
                    .filter(message -> message.getConversationId().equals(conversationId))
                    .filter(message -> message.getSeq() < beforeSeq)
                    .sorted(Comparator.comparing(MessageEntity::getSeq).reversed())
                    .limit(pageable.getPageSize())
                    .toList();
        });
    }

    private java.util.List<MessageEntity> messagesByConversationDesc(UUID conversationId, Pageable pageable) {
        return messagesById.values()
                .stream()
                .filter(message -> message.getConversationId().equals(conversationId))
                .sorted(Comparator.comparing(MessageEntity::getSeq).reversed())
                .limit(pageable.getPageSize())
                .toList();
    }

    private TestLogin registerAndLogin(String emailPrefix) {
        String suffix = UUID.randomUUID().toString();
        String email = emailPrefix + "." + suffix + "@example.com";
        UUID deviceId = UUID.randomUUID();

        authService.register(new RegisterRequest(email, "password123", emailPrefix + " User"));
        LoginResponse login = authService.login(new LoginRequest(email, "password123", deviceId, "ios"));

        return new TestLogin(login.user().id(), login.accessToken());
    }

    private UUID seedDirectConversation(UUID currentUserId, UUID peerUserId) {
        Instant createdAt = Instant.parse("2026-05-16T00:00:00Z");
        UUID conversationId = UUID.randomUUID();
        String directKey = DirectConversationKeyUtil.build(currentUserId, peerUserId);

        conversationRepository.save(new ConversationEntity(conversationId, directKey, 0L, null, createdAt, createdAt));
        conversationMemberRepository.save(new ConversationMemberEntity(
                conversationId,
                currentUserId,
                0L,
                0L,
                0,
                createdAt,
                createdAt
        ));
        conversationMemberRepository.save(new ConversationMemberEntity(
                conversationId,
                peerUserId,
                0L,
                0L,
                0,
                createdAt,
                createdAt
        ));

        Instant lastMessageAt = createdAt;
        for (long seq = 1; seq <= 25; seq++) {
            Instant messageCreatedAt = createdAt.plusSeconds(seq);
            lastMessageAt = messageCreatedAt;
            messageRepository.save(new MessageEntity(
                    UUID.randomUUID(),
                    conversationId,
                    seq,
                    currentUserId,
                    UUID.randomUUID(),
                    "message-" + seq,
                    messageCreatedAt
            ));
        }

        conversationRepository.save(new ConversationEntity(
                conversationId,
                directKey,
                25L,
                lastMessageAt,
                createdAt,
                lastMessageAt
        ));
        return conversationId;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestLogin(UUID userId, String accessToken) {
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
