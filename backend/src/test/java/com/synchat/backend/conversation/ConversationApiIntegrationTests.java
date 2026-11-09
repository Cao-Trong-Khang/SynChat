package com.synchat.backend.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.synchat.backend.auth.dto.LoginRequest;
import com.synchat.backend.auth.dto.LoginResponse;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.conversation.dto.ConversationMetadataRow;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.conversation.util.DirectConversationKeyUtil;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.entity.MessageEntity;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import com.synchat.backend.realtime.typing.service.TypingService;
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
import org.springframework.test.web.servlet.MvcResult;
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
class ConversationApiIntegrationTests {

    @MockBean
    private TypingService typingService;

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
    private SyncService syncService;

    @MockBean
    private MessageSendService messageSendService;

    @MockBean
    private OutboxEventAppender outboxEventAppender;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private UserEventAppender userEventAppender;

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

        setUpAuthRepositories();
        setUpConversationRepositories();
    }

    @Test
    void directConversationWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/conversations/direct")
                .param("peerUserId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void directConversationWithTokenAndNoExistingConversationReturnsNullConversation() throws Exception {
        TestLogin alice = registerAndLogin("alice");
        TestLogin bob = registerAndLogin("bob");

        mockMvc.perform(get("/api/conversations/direct")
                .param("peerUserId", bob.userId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation", nullValue()));
    }

    @Test
    void directConversationWithExistingConversationReturnsMetadata() throws Exception {
        TestLogin alice = registerAndLogin("alice");
        TestLogin bob = registerAndLogin("bob");
        Instant lastMessageAt = Instant.parse("2026-05-16T01:02:03Z");
        ConversationEntity conversation = seedConversation(
                alice.userId(),
                bob.userId(),
                alice.userId(),
                "hello bob",
                lastMessageAt,
                3,
                4L,
                5L,
                6L
        );

        mockMvc.perform(get("/api/conversations/direct")
                .param("peerUserId", bob.userId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.conversationId", is(conversation.getId().toString())))
                .andExpect(jsonPath("$.conversation.peer.id", is(bob.userId().toString())))
                .andExpect(jsonPath("$.conversation.peer.email", is(bob.email())))
                .andExpect(jsonPath("$.conversation.peer.displayName", is(bob.displayName())))
                .andExpect(jsonPath("$.conversation.peer.avatarUrl", nullValue()))
                .andExpect(jsonPath("$.conversation.lastMessageSeq", is(1)))
                .andExpect(jsonPath("$.conversation.lastMessageText", is("hello bob")))
                .andExpect(jsonPath("$.conversation.lastMessageSenderId", is(alice.userId().toString())))
                .andExpect(jsonPath("$.conversation.lastMessageAt", is(lastMessageAt.toString())))
                .andExpect(jsonPath("$.conversation.unreadCount", is(3)))
                .andExpect(jsonPath("$.conversation.peerReadSeq", is(5)))
                .andExpect(jsonPath("$.conversation.peerDeliveredSeq", is(6)));
    }

    @Test
    void syncConversationsFirstPageReturnsItemsCursorHasMoreAndServerEventSeq() throws Exception {
        TestLogin alice = registerAndLogin("alice");
        TestLogin bob = registerAndLogin("bob");
        TestLogin charlie = registerAndLogin("charlie");
        seedConversation(
                alice.userId(),
                bob.userId(),
                bob.userId(),
                "latest message",
                Instant.parse("2026-05-16T02:00:00Z"),
                1,
                0L,
                1L,
                1L
        );
        seedConversation(
                alice.userId(),
                charlie.userId(),
                charlie.userId(),
                "older message",
                Instant.parse("2026-05-16T01:00:00Z"),
                2,
                0L,
                1L,
                1L
        );

        mockMvc.perform(get("/api/sync/conversations")
                .param("limit", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].peer.email", is(bob.email())))
                .andExpect(jsonPath("$.items[0].lastMessageText", is("latest message")))
                .andExpect(jsonPath("$.nextCursor", not("")))
                .andExpect(jsonPath("$.hasMore", is(true)))
                .andExpect(jsonPath("$.serverEventSeq", is(0)));
    }

    @Test
    void syncConversationsWithCursorReturnsLoadMorePageWithoutServerEventSeq() throws Exception {
        TestLogin alice = registerAndLogin("alice");
        TestLogin bob = registerAndLogin("bob");
        TestLogin charlie = registerAndLogin("charlie");
        seedConversation(
                alice.userId(),
                bob.userId(),
                bob.userId(),
                "latest message",
                Instant.parse("2026-05-16T02:00:00Z"),
                1,
                0L,
                1L,
                1L
        );
        seedConversation(
                alice.userId(),
                charlie.userId(),
                charlie.userId(),
                "older message",
                Instant.parse("2026-05-16T01:00:00Z"),
                2,
                0L,
                1L,
                1L
        );

        MvcResult firstPage = mockMvc.perform(get("/api/sync/conversations")
                .param("limit", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        String cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.nextCursor");

        MvcResult loadMore = mockMvc.perform(get("/api/sync/conversations")
                .param("limit", "1")
                .param("cursor", cursor)
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].peer.email", is(charlie.email())))
                .andExpect(jsonPath("$.items[0].lastMessageText", is("older message")))
                .andExpect(jsonPath("$.nextCursor", nullValue()))
                .andExpect(jsonPath("$.hasMore", is(false)))
                .andReturn();

        String body = loadMore.getResponse().getContentAsString();
        assertThat(body).satisfies(response ->
                assertThat(!response.contains("serverEventSeq") || response.contains("\"serverEventSeq\":null"))
                        .isTrue());
    }

    @Test
    void syncConversationsDoesNotReturnMessagesArray() throws Exception {
        TestLogin alice = registerAndLogin("alice");
        TestLogin bob = registerAndLogin("bob");
        seedConversation(
                alice.userId(),
                bob.userId(),
                bob.userId(),
                "no messages array here",
                Instant.parse("2026-05-16T03:00:00Z"),
                1,
                0L,
                1L,
                1L
        );

        mockMvc.perform(get("/api/sync/conversations")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].messages").doesNotExist());
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

        when(userEventOffsetRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(offsetsByUserId.get(invocation.getArgument(0))));
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
        when(conversationRepository.findByDirectKey(any()))
                .thenAnswer(invocation -> Optional.ofNullable(conversationsByDirectKey.get(invocation.getArgument(0))));
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversationsById.put(conversation.getId(), conversation);
            conversationsByDirectKey.put(conversation.getDirectKey(), conversation);
            return conversation;
        });
        when(conversationRepository.findLatestConversationsForUser(any(), any()))
                .thenAnswer(invocation -> conversationsForUser(invocation.getArgument(0), invocation.getArgument(1))
                        .toList());
        when(conversationRepository.findConversationsForUserAfterCursor(any(), any(), any(), any()))
                .thenAnswer(invocation -> conversationsForUser(invocation.getArgument(0), invocation.getArgument(3))
                        .filter(conversation -> isAfterCursor(
                                conversation,
                                invocation.getArgument(1),
                                invocation.getArgument(2)
                        ))
                        .toList());
        when(conversationRepository.findMetadataLatestForUser(any(), any()))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    return conversationsForUser(userId, invocation.getArgument(1))
                            .map(c -> toMetadataRow(c, userId))
                            .toList();
                });
        when(conversationRepository.findMetadataAfterCursorForUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    return conversationsForUser(userId, invocation.getArgument(3))
                            .filter(conversation -> isAfterCursor(
                                    conversation,
                                    invocation.getArgument(1),
                                    invocation.getArgument(2)
                            ))
                            .map(c -> toMetadataRow(c, userId))
                            .toList();
                });

        when(conversationMemberRepository.save(any())).thenAnswer(invocation -> {
            ConversationMemberEntity member = invocation.getArgument(0);
            membersById.put(new ConversationMemberId(member.getConversationId(), member.getUserId()), member);
            return member;
        });
        when(conversationMemberRepository.findByConversationId(any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            return membersById.values()
                    .stream()
                    .filter(member -> member.getConversationId().equals(conversationId))
                    .toList();
        });
        when(conversationMemberRepository.findByConversationIdAndUserId(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            return Optional.ofNullable(membersById.get(new ConversationMemberId(conversationId, userId)));
        });

        when(messageRepository.save(any())).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            messagesById.put(message.getId(), message);
            return message;
        });
        when(messageRepository.findByConversationIdAndSeq(any(), any())).thenAnswer(invocation -> {
            UUID conversationId = invocation.getArgument(0);
            Long seq = invocation.getArgument(1);
            return messagesById.values()
                    .stream()
                    .filter(message -> message.getConversationId().equals(conversationId))
                    .filter(message -> message.getSeq().equals(seq))
                    .findFirst();
        });
    }

    private Stream<ConversationEntity> conversationsForUser(UUID userId, Pageable pageable) {
        return conversationsById.values()
                .stream()
                .filter(conversation -> conversation.getLastMessageAt() != null)
                .filter(conversation -> membersById.containsKey(new ConversationMemberId(conversation.getId(), userId)))
                .sorted(Comparator.comparing(ConversationEntity::getLastMessageAt)
                        .reversed()
                        .thenComparing(ConversationEntity::getId, Comparator.reverseOrder()))
                .limit(pageable.getPageSize());
    }

    private boolean isAfterCursor(
            ConversationEntity conversation,
            Instant cursorLastMessageAt,
            UUID cursorConversationId
    ) {
        return conversation.getLastMessageAt().isBefore(cursorLastMessageAt)
                || (conversation.getLastMessageAt().equals(cursorLastMessageAt)
                && conversation.getId().compareTo(cursorConversationId) < 0);
    }

    private ConversationMetadataRow toMetadataRow(ConversationEntity conversation, UUID currentUserId) {
        ConversationMemberEntity currentMember = membersById.get(new ConversationMemberId(conversation.getId(), currentUserId));
        ConversationMemberEntity peerMember = membersById.values().stream()
                .filter(m -> m.getConversationId().equals(conversation.getId()) && !m.getUserId().equals(currentUserId))
                .findFirst()
                .orElse(null);
        UserEntity peer = peerMember != null ? usersById.get(peerMember.getUserId()) : null;
        MessageEntity lastMessage = conversation.getLastMessageSeq() != null && conversation.getLastMessageSeq() > 0
                ? messagesById.values().stream()
                        .filter(m -> m.getConversationId().equals(conversation.getId()) && m.getSeq().equals(conversation.getLastMessageSeq()))
                        .findFirst()
                        .orElse(null)
                : null;

        return new ConversationMetadataRow(
                conversation.getId(),
                peer != null ? peer.getId() : null,
                peer != null ? peer.getEmail() : null,
                peer != null ? peer.getDisplayName() : null,
                peer != null ? peer.getAvatarUrl() : null,
                conversation.getLastMessageSeq(),
                lastMessage != null ? lastMessage.getBody() : null,
                lastMessage != null ? lastMessage.getSenderId() : null,
                conversation.getLastMessageAt(),
                currentMember != null ? currentMember.getUnreadCount() : 0,
                peerMember != null ? peerMember.getReadSeq() : 0L,
                peerMember != null ? peerMember.getDeliveredSeq() : 0L
        );
    }

    private TestLogin registerAndLogin(String displayNamePrefix) {
        String suffix = UUID.randomUUID().toString();
        String email = displayNamePrefix + "." + suffix + "@example.com";
        String displayName = Character.toUpperCase(displayNamePrefix.charAt(0))
                + displayNamePrefix.substring(1)
                + " "
                + suffix.substring(0, 8);
        UUID deviceId = UUID.randomUUID();

        authService.register(new RegisterRequest(email, "password123", displayName));
        LoginResponse login = authService.login(new LoginRequest(email, "password123", deviceId, "ios"));

        return new TestLogin(login.user().id(), email, displayName, deviceId, login.accessToken());
    }

    private ConversationEntity seedConversation(
            UUID currentUserId,
            UUID peerUserId,
            UUID senderId,
            String messageBody,
            Instant lastMessageAt,
            int unreadCount,
            long currentReadSeq,
            long peerReadSeq,
            long peerDeliveredSeq
    ) {
        Instant createdAt = Instant.parse("2026-05-16T00:00:00Z");
        UUID conversationId = UUID.randomUUID();
        String directKey = DirectConversationKeyUtil.build(currentUserId, peerUserId);
        ConversationEntity initialConversation = new ConversationEntity(
                conversationId,
                directKey,
                0L,
                null,
                createdAt,
                createdAt
        );
        conversationRepository.save(initialConversation);

        conversationMemberRepository.save(new ConversationMemberEntity(
                conversationId,
                currentUserId,
                currentReadSeq,
                currentReadSeq,
                unreadCount,
                createdAt,
                createdAt
        ));
        conversationMemberRepository.save(new ConversationMemberEntity(
                conversationId,
                peerUserId,
                peerReadSeq,
                peerDeliveredSeq,
                0,
                createdAt,
                createdAt
        ));

        messageRepository.save(new MessageEntity(
                UUID.randomUUID(),
                conversationId,
                1L,
                senderId,
                UUID.randomUUID(),
                messageBody,
                lastMessageAt
        ));

        ConversationEntity updatedConversation = new ConversationEntity(
                conversationId,
                directKey,
                1L,
                lastMessageAt,
                createdAt,
                lastMessageAt
        );
        return conversationRepository.save(updatedConversation);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TestLogin(UUID userId, String email, String displayName, UUID deviceId, String accessToken) {
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
