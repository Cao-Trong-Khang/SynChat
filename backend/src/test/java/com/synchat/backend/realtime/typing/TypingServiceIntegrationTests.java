package com.synchat.backend.realtime.typing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.conversation.entity.ConversationEntity;
import com.synchat.backend.conversation.entity.ConversationMemberEntity;
import com.synchat.backend.conversation.entity.ConversationMemberId;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.conversation.repository.ConversationRepository;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.repository.MessageRepository;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.realtime.typing.service.TypingRuntimeStore;
import com.synchat.backend.realtime.typing.service.TypingService;
import com.synchat.backend.realtime.typing.service.TypingSubscriptionStore;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class TypingServiceIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(7);

    @Autowired
    private TypingService typingService;

    @Autowired
    private TypingSubscriptionStore typingSubscriptionStore;

    @Autowired
    private TypingRuntimeStore typingRuntimeStore;

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
    private final Map<UUID, ConversationEntity> conversationsById = new HashMap<>();
    private final Map<ConversationMemberId, ConversationMemberEntity> membersById = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();
    private final Map<String, ExpiringValue> redisValues = new HashMap<>();
    private final Set<String> socketIds = new HashSet<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        conversationsById.clear();
        membersById.clear();
        redisSets.clear();
        redisValues.clear();
        socketIds.clear();

        setUpRedis();
        setUpRepositories();
    }

    @AfterEach
    void tearDown() {
        socketIds.forEach(webSocketSessionRegistry::unregister);
    }

    @Test
    void subscribeStoresSocketSubscription() {
        ConversationFixture fixture = createConversationFixture();
        UserSessionBinding bindingA = registerSocket("socket-a", fixture.userA().id());

        typingService.subscribe(bindingA.socketId(), bindingA.userId(), fixture.conversation().getId());

        assertThat(typingSubscriptionStore.findSubscriberSocketIds(fixture.conversation().getId()))
                .contains(bindingA.socketId());
        assertNoPersistedEvents();
    }

    @Test
    void subscribeRejectsNonMember() {
        ConversationFixture fixture = createConversationFixture();
        TestUser userC = createUser("c");

        assertThatThrownBy(() -> typingService.subscribe("socket-c", userC.id(), fixture.conversation().getId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertNoPersistedEvents();
    }

    @Test
    void startTypingStoresRedisState() {
        ConversationFixture fixture = createConversationFixture();
        UserSessionBinding bindingA = registerSocket("socket-a", fixture.userA().id());

        typingService.startTyping(bindingA, fixture.conversation().getId());

        assertThat(typingRuntimeStore.isTyping(fixture.conversation().getId(), fixture.userA().id())).isTrue();
        assertNoPersistedEvents();
    }

    @Test
    void stopTypingClearsRedisState() {
        ConversationFixture fixture = createConversationFixture();
        UserSessionBinding bindingA = registerSocket("socket-a", fixture.userA().id());

        typingService.startTyping(bindingA, fixture.conversation().getId());
        typingService.stopTyping(bindingA, fixture.conversation().getId());

        assertThat(typingRuntimeStore.isTyping(fixture.conversation().getId(), fixture.userA().id())).isFalse();
        assertNoPersistedEvents();
    }

    @Test
    void handleSocketDisconnectedClearsActiveTypingStateAndSubscription() {
        ConversationFixture fixture = createConversationFixture();
        UserSessionBinding bindingA = registerSocket("socket-a", fixture.userA().id());
        typingService.subscribe(bindingA.socketId(), bindingA.userId(), fixture.conversation().getId());
        typingService.startTyping(bindingA, fixture.conversation().getId());

        typingService.handleSocketDisconnected(bindingA);

        assertThat(typingRuntimeStore.isTyping(fixture.conversation().getId(), fixture.userA().id())).isFalse();
        assertThat(typingSubscriptionStore.findSubscriberSocketIds(fixture.conversation().getId()))
                .doesNotContain(bindingA.socketId());
        assertNoPersistedEvents();
    }

    @Test
    void ttlExpirationEventuallyClearsTypingState() throws Exception {
        ConversationFixture fixture = createConversationFixture();
        UserSessionBinding bindingA = registerSocket("socket-a", fixture.userA().id());

        typingService.startTyping(bindingA, fixture.conversation().getId());

        awaitUntil(() -> !typingRuntimeStore.isTyping(fixture.conversation().getId(), fixture.userA().id()));
        assertNoPersistedEvents();
    }

    private ConversationFixture createConversationFixture() {
        TestUser userA = createUser("a");
        TestUser userB = createUser("b");
        Instant now = Instant.now();
        ConversationEntity conversation = ConversationEntity.create(UUID.randomUUID(), "direct:test:" + UUID.randomUUID(), now);
        conversationsById.put(conversation.getId(), conversation);
        saveMember(ConversationMemberEntity.create(conversation.getId(), userA.id(), now));
        saveMember(ConversationMemberEntity.create(conversation.getId(), userB.id(), now));
        return new ConversationFixture(userA, userB, conversation);
    }

    private TestUser createUser(String prefix) {
        UserEntity user = UserEntity.create(
                UUID.randomUUID(),
                prefix + "." + UUID.randomUUID() + "@example.com",
                prefix + " User",
                null,
                Instant.now()
        );
        usersById.put(user.getId(), user);
        return new TestUser(user.getId());
    }

    private UserSessionBinding registerSocket(String socketId, UUID userId) {
        socketIds.add(socketId);
        webSocketSessionRegistry.register(socketId, new AuthPrincipal(userId, UUID.randomUUID(), UUID.randomUUID()));
        return webSocketSessionRegistry.findBySocketId(socketId).orElseThrow();
    }

    private void saveMember(ConversationMemberEntity member) {
        membersById.put(new ConversationMemberId(member.getConversationId(), member.getUserId()), member);
    }

    private void setUpRepositories() {
        when(userRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            return streamIds(ids)
                    .map(usersById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        });
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

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private void assertNoPersistedEvents() {
        verify(userEventRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private record TestUser(UUID id) {
    }

    private record ConversationFixture(TestUser userA, TestUser userB, ConversationEntity conversation) {
    }

    private record ExpiringValue(String value, long expiresAtNanos) {
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
