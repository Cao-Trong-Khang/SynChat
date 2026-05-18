package com.synchat.backend.user;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.sync.service.UserEventAppender;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
class UserSearchIntegrationTests {

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
    private ConversationMetadataMapper conversationMetadataMapper;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private SyncService syncService;

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
    private final Map<UUID, DeviceEntity> devicesById = new HashMap<>();
    private final Map<UUID, AuthSessionEntity> sessionsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        usersById.clear();
        usersByEmail.clear();
        credentialsByUserId.clear();
        offsetsByUserId.clear();
        devicesById.clear();
        sessionsById.clear();

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
        when(userRepository.searchUsers(any(), any(), any())).thenAnswer(invocation -> {
            UUID currentUserId = invocation.getArgument(0);
            String keyword = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(2);
            String needle = keyword.replace("%", "").toLowerCase(Locale.ROOT);

            return usersById.values()
                    .stream()
                    .filter(user -> !user.getId().equals(currentUserId))
                    .filter(user -> user.getEmail().toLowerCase(Locale.ROOT).contains(needle)
                            || user.getDisplayName().toLowerCase(Locale.ROOT).contains(needle))
                    .sorted(Comparator.comparing(UserEntity::getDisplayName).thenComparing(UserEntity::getEmail))
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .toList();
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

    @Test
    void searchWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "bo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void searchWithValidTokenReturnsMatchingUsers() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String aliceEmail = "alice-" + suffix + "@example.com";
        String bobEmail = "bob-" + suffix + "@example.com";
        authService.register(new RegisterRequest(aliceEmail, "password123", "Alice User"));
        authService.register(new RegisterRequest(bobEmail, "password123", "Bob User"));
        LoginResponse aliceLogin = login(aliceEmail);

        mockMvc.perform(get("/api/users/search")
                .param("q", "bo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceLogin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email", hasItem(bobEmail)))
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].email").exists())
                .andExpect(jsonPath("$.items[0].displayName").exists())
                .andExpect(jsonPath("$.items[0].avatarUrl").hasJsonPath());
    }

    @Test
    void searchExcludesCurrentUser() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String aliceEmail = "alice-" + suffix + "@example.com";
        authService.register(new RegisterRequest(aliceEmail, "password123", "Alice User"));
        LoginResponse aliceLogin = login(aliceEmail);

        mockMvc.perform(get("/api/users/search")
                .param("q", "alice")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceLogin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email", everyItem(not(containsString(aliceEmail)))));
    }

    @Test
    void blankKeywordReturnsValidationError() throws Exception {
        LoginResponse login = registeredLogin();

        mockMvc.perform(get("/api/users/search")
                .param("q", "")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oneCharacterKeywordReturnsValidationError() throws Exception {
        LoginResponse login = registeredLogin();

        mockMvc.perform(get("/api/users/search")
                .param("q", "a")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private LoginResponse registeredLogin() {
        String email = "alice-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password123", "Alice User"));
        return login(email);
    }

    private LoginResponse login(String email) {
        return authService.login(new LoginRequest(email, "password123", UUID.randomUUID(), "ios"));
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
