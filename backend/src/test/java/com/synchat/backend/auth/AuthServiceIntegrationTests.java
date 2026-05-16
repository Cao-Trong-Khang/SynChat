package com.synchat.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synchat.backend.auth.dto.LoginRequest;
import com.synchat.backend.auth.dto.LoginResponse;
import com.synchat.backend.auth.dto.LogoutRequest;
import com.synchat.backend.auth.dto.RefreshSessionRequest;
import com.synchat.backend.auth.dto.RefreshSessionResponse;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.auth.service.AuthService;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.conversation.service.ConversationMetadataMapper;
import com.synchat.backend.conversation.service.ConversationService;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.message.service.MessagePaginationService;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.service.SyncService;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
class AuthServiceIntegrationTests {

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
    void registerCreatesUserCredentialAndOffsetWithoutAuthSession() {
        authService.register(new RegisterRequest(" USER@example.com ", "password123", " Test User "));

        assertThat(usersByEmail).containsKey("user@example.com");
        UserEntity user = usersByEmail.get("user@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(credentialsByUserId).containsKey(user.getId());
        assertThat(offsetsByUserId).containsKey(user.getId());
        assertThat(offsetsByUserId.get(user.getId()).getLastEventSeq()).isZero();
        verify(authSessionRepository, never()).save(any());
    }

    @Test
    void duplicateRegisterThrowsEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "Test User");

        authService.register(request);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void loginReturnsTokensAndCreatesDeviceAndAuthSession() {
        authService.register(new RegisterRequest("user@example.com", "password123", "Test User"));
        UUID deviceId = UUID.randomUUID();

        LoginResponse response = authService
                .login(new LoginRequest("user@example.com", "password123", deviceId, "ios"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(devicesById).containsKey(deviceId);
        assertThat(devicesById.get(deviceId).getPushToken()).isNull();
        assertThat(devicesById.get(deviceId).getPushProvider()).isNull();
        assertThat(sessionsById).hasSize(1);
        assertThat(sessionsById.values().iterator().next().getRevokedAt()).isNull();
    }

    @Test
    void refreshRotatesRefreshTokenAndOldRefreshTokenNoLongerWorks() {
        authService.register(new RegisterRequest("user@example.com", "password123", "Test User"));
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest("user@example.com", "password123", deviceId, "ios"));

        RefreshSessionResponse refresh = authService.refresh(new RefreshSessionRequest(login.refreshToken(), deviceId));

        assertThat(refresh.accessToken()).isNotBlank();
        assertThat(refresh.refreshToken()).isNotBlank();
        assertThat(refresh.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThatThrownBy(() -> authService.refresh(new RefreshSessionRequest(login.refreshToken(), deviceId)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void logoutRevokesSessionAndRefreshAfterLogoutFails() {
        authService.register(new RegisterRequest("user@example.com", "password123", "Test User"));
        UUID deviceId = UUID.randomUUID();
        LoginResponse login = authService.login(new LoginRequest("user@example.com", "password123", deviceId, "ios"));

        assertThat(authService.logout(new LogoutRequest(login.refreshToken(), deviceId)).success()).isTrue();

        AuthSessionEntity session = sessionsById.values().iterator().next();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> authService.refresh(new RefreshSessionRequest(login.refreshToken(), deviceId)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
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
