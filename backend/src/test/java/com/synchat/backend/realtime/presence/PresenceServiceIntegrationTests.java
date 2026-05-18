package com.synchat.backend.realtime.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.realtime.presence.dto.PresenceChangedPayload;
import com.synchat.backend.realtime.presence.dto.PresenceSnapshotPayload;
import com.synchat.backend.realtime.presence.model.PresenceEventTypes;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.realtime.presence.service.PresenceSubscriptionStore;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.synchat.backend.realtime.typing.service.TypingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
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
class PresenceServiceIntegrationTests {

    @MockBean
    private TypingService typingService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private PresenceSubscriptionStore presenceSubscriptionStore;

    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;

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
    private EphemeralWebSocketEventSender ephemeralWebSocketEventSender;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = org.mockito.Mockito.mock(SetOperations.class);

    private final Map<UUID, UserEntity> usersById = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        redisSets.clear();
        setUpRedis();
        setUpUsers();
    }

    @Test
    void subscribeReturnsSnapshotForOfflineUser() {
        TestUser userA = createUser("a");
        TestUser userB = createUser("b");

        PresenceSnapshotPayload snapshot = presenceService.subscribe("socket-a", List.of(userB.id()));

        assertThat(snapshot.items()).hasSize(1);
        assertThat(snapshot.items().get(0).userId()).isEqualTo(userB.id());
        assertThat(snapshot.items().get(0).online()).isFalse();
        assertThat(snapshot.items().get(0).lastSeenAt()).isNull();
        assertThat(presenceSubscriptionStore.findWatcherSocketIds(userB.id())).contains("socket-a");
    }

    @Test
    void subscribeValidatesUserExistence() {
        assertThatThrownBy(() -> presenceService.subscribe("socket-a", List.of(UUID.randomUUID())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void socketConnectedChangesOfflineToOnline() {
        TestUser userA = createUser("a");
        TestUser userB = createUser("b");
        presenceService.subscribe("socket-a", List.of(userB.id()));

        UserSessionBinding bindingB = registerSocket("socket-b", userB.id());
        presenceService.handleSocketConnected(bindingB, false);

        ArgumentCaptor<PresenceChangedPayload> payloadCaptor = ArgumentCaptor.forClass(PresenceChangedPayload.class);
        verify(ephemeralWebSocketEventSender).sendToSocket(
                eq("socket-a"),
                eq(PresenceEventTypes.PRESENCE_CHANGED),
                eq(null),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue().userId()).isEqualTo(userB.id());
        assertThat(payloadCaptor.getValue().online()).isTrue();
        assertThat(payloadCaptor.getValue().lastSeenAt()).isNull();
    }

    @Test
    void socketDisconnectedUpdatesLastSeenAtWhenNoSocketsRemain() {
        TestUser userB = createUser("b");
        UserSessionBinding bindingB = registerSocket("socket-b", userB.id());

        webSocketSessionRegistry.unregister(bindingB.socketId());
        presenceService.handleSocketDisconnected(bindingB);

        assertThat(usersById.get(userB.id()).getLastSeenAt()).isNotNull();
    }

    @Test
    void socketDisconnectedDoesNotUpdateLastSeenAtWhenAnotherSocketRemainsOnline() {
        TestUser userB = createUser("b");
        UserSessionBinding binding1 = registerSocket("socket-b-1", userB.id());
        registerSocket("socket-b-2", userB.id());

        webSocketSessionRegistry.unregister(binding1.socketId());
        presenceService.handleSocketDisconnected(binding1);

        assertThat(usersById.get(userB.id()).getLastSeenAt()).isNull();
    }

    @Test
    void unsubscribeRemovesSubscription() {
        TestUser userA = createUser("a");
        TestUser userB = createUser("b");
        presenceService.subscribe("socket-a", List.of(userB.id()));

        presenceService.unsubscribe("socket-a", List.of(userB.id()));

        assertThat(presenceSubscriptionStore.findWatcherSocketIds(userB.id())).doesNotContain("socket-a");
    }

    @Test
    void unsubscribeAllRemovesAllSubscriptionsForSocket() {
        TestUser userA = createUser("a");
        TestUser userB = createUser("b");
        TestUser userC = createUser("c");
        presenceService.subscribe("socket-a", List.of(userB.id(), userC.id()));

        presenceSubscriptionStore.unsubscribeAll("socket-a");

        assertThat(presenceSubscriptionStore.findWatcherSocketIds(userB.id())).doesNotContain("socket-a");
        assertThat(presenceSubscriptionStore.findWatcherSocketIds(userC.id())).doesNotContain("socket-a");
    }

    private UserSessionBinding registerSocket(String socketId, UUID userId) {
        AuthPrincipal principal = new AuthPrincipal(userId, UUID.randomUUID(), UUID.randomUUID());
        webSocketSessionRegistry.register(socketId, principal);
        return webSocketSessionRegistry.findBySocketId(socketId).orElseThrow();
    }

    private TestUser createUser(String prefix) {
        Instant now = Instant.now();
        UserEntity user = UserEntity.create(
                UUID.randomUUID(),
                prefix + "." + UUID.randomUUID() + "@example.com",
                prefix + " User",
                null,
                now
        );
        usersById.put(user.getId(), user);
        return new TestUser(user.getId());
    }

    private void setUpUsers() {
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
            return user;
        });
    }

    private java.util.stream.Stream<UUID> streamIds(Iterable<UUID> ids) {
        return ids instanceof Collection<UUID> collection
                ? collection.stream()
                : java.util.stream.StreamSupport.stream(ids.spliterator(), false);
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

    private record TestUser(UUID id) {
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
