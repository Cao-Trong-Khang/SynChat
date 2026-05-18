package com.synchat.backend.sync;

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
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
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
class SyncEventsIntegrationTests {

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
    private UserEventRepository userEventRepository;

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private ConversationMetadataMapper conversationMetadataMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private MessagePaginationService messagePaginationService;

    @MockBean
    private MessageSendService messageSendService;

    @MockBean
    private OutboxEventAppender outboxEventAppender;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private UserEventAppender userEventAppender;

    @MockBean
    private ReceiptService receiptService;

    private final Map<UUID, UserEntity> usersById = new HashMap<>();
    private final Map<String, UserEntity> usersByEmail = new HashMap<>();
    private final Map<UUID, UserCredentialEntity> credentialsByUserId = new HashMap<>();
    private final Map<UUID, UserEventOffsetEntity> offsetsByUserId = new HashMap<>();
    private final Map<UserEventId, UserEventEntity> eventsById = new HashMap<>();
    private final Map<UUID, DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        eventsById.clear();
        devicesById.clear();
        sessionsById.clear();

        setUpAuthRepositories();
        setUpEventRepositories();
    }

    @Test
    void syncEventsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "500"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void syncEventsWithNoEventsReturnsEmptyResponse() throws Exception {
        TestLogin login = registerAndLogin();

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(0)))
                .andExpect(jsonPath("$.hasMore", is(false)))
                .andExpect(jsonPath("$.nextAfterSeq", is(0)));
    }

    @Test
    void syncEventsReturnsEventsAfterSeqSortedAscending() throws Exception {
        TestLogin login = registerAndLogin();
        seedEvents(login.userId(), 1L, 2L, 3L);
        setLastEventSeq(login.userId(), 3L);

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "1")
                .param("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventSeq", is(2)))
                .andExpect(jsonPath("$.events[1].eventSeq", is(3)))
                .andExpect(jsonPath("$.events[0].payload.message", is("event-2")))
                .andExpect(jsonPath("$.hasMore", is(false)))
                .andExpect(jsonPath("$.nextAfterSeq", is(3)));
    }

    @Test
    void syncEventsPaginationWorks() throws Exception {
        TestLogin login = registerAndLogin();
        seedEvents(login.userId(), 1L, 2L, 3L);
        setLastEventSeq(login.userId(), 3L);

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventSeq", is(1)))
                .andExpect(jsonPath("$.events[1].eventSeq", is(2)))
                .andExpect(jsonPath("$.hasMore", is(true)))
                .andExpect(jsonPath("$.nextAfterSeq", is(2)));

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "2")
                .param("limit", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventSeq", is(3)))
                .andExpect(jsonPath("$.hasMore", is(false)))
                .andExpect(jsonPath("$.nextAfterSeq", is(3)));
    }

    @Test
    void invalidAfterSeqReturnsValidationError() throws Exception {
        TestLogin login = registerAndLogin();

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "-1")
                .param("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void invalidLimitReturnsValidationError() throws Exception {
        TestLogin login = registerAndLogin();

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "1001")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void afterSeqGreaterThanServerEventSeqReturnsValidationError() throws Exception {
        TestLogin login = registerAndLogin();
        setLastEventSeq(login.userId(), 3L);

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "4")
                .param("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void expiredCursorReturnsEventCursorExpired() throws Exception {
        TestLogin login = registerAndLogin();
        seedEvent(login.userId(), 8L);
        seedEvent(login.userId(), 9L);
        seedEvent(login.userId(), 10L);
        setLastEventSeq(login.userId(), 10L);

        mockMvc.perform(get("/api/sync/events")
                .param("afterSeq", "0")
                .param("limit", "500")
                .header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("EVENT_CURSOR_EXPIRED")));
    }

    private void setUpAuthRepositories() {
        when(userRepository.existsByEmail(any()))
                .thenAnswer(invocation -> usersByEmail.containsKey(invocation.getArgument(0)));
        when(userRepository.findByEmail(any()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
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

    private void setUpEventRepositories() {
        when(userEventOffsetRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(offsetsByUserId.get(invocation.getArgument(0))));
        when(userEventOffsetRepository.save(any())).thenAnswer(invocation -> {
            UserEventOffsetEntity offset = invocation.getArgument(0);
            offsetsByUserId.put(offset.getUserId(), offset);
            return offset;
        });

        when(userEventRepository.save(any())).thenAnswer(invocation -> {
            UserEventEntity event = invocation.getArgument(0);
            eventsById.put(new UserEventId(event.getUserId(), event.getEventSeq()), event);
            return event;
        });
        when(userEventRepository.findByUserIdAndEventSeqGreaterThanOrderByEventSeqAsc(any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID userId = invocation.getArgument(0);
                    Long afterSeq = invocation.getArgument(1);
                    Pageable pageable = invocation.getArgument(2);
                    return eventsById.values()
                            .stream()
                            .filter(event -> event.getUserId().equals(userId))
                            .filter(event -> event.getEventSeq() > afterSeq)
                            .sorted(Comparator.comparing(UserEventEntity::getEventSeq))
                            .limit(pageable.getPageSize())
                            .toList();
                });
        when(userEventRepository.findMinEventSeqByUserId(any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return eventsById.values()
                    .stream()
                    .filter(event -> event.getUserId().equals(userId))
                    .map(UserEventEntity::getEventSeq)
                    .min(Long::compareTo);
        });
        when(userEventRepository.countByUserIdAndEventSeqGreaterThan(any(), any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            Long afterSeq = invocation.getArgument(1);
            return eventsById.values()
                    .stream()
                    .filter(event -> event.getUserId().equals(userId))
                    .filter(event -> event.getEventSeq() > afterSeq)
                    .count();
        });
    }

    private TestLogin registerAndLogin() {
        String email = "sync-events-" + UUID.randomUUID() + "@example.com";
        UUID deviceId = UUID.randomUUID();
        authService.register(new RegisterRequest(email, "password123", "Sync Events User"));
        LoginResponse login = authService.login(new LoginRequest(email, "password123", deviceId, "ios"));
        return new TestLogin(login.user().id(), login.accessToken());
    }

    private void seedEvents(UUID userId, Long... eventSeqs) {
        for (Long eventSeq : eventSeqs) {
            seedEvent(userId, eventSeq);
        }
    }

    private void seedEvent(UUID userId, Long eventSeq) {
        userEventRepository.save(new UserEventEntity(
                userId,
                eventSeq,
                "message.created",
                "{\"message\":\"event-" + eventSeq + "\",\"seq\":" + eventSeq + "}",
                Instant.parse("2026-05-16T00:00:00Z").plusSeconds(eventSeq)
        ));
    }

    private void setLastEventSeq(UUID userId, Long lastEventSeq) {
        userEventOffsetRepository.save(new UserEventOffsetEntity(
                userId,
                lastEventSeq,
                Instant.parse("2026-05-16T00:00:00Z")
        ));
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
