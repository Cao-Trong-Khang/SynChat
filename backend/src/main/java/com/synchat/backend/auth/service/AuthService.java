package com.synchat.backend.auth.service;

import com.synchat.backend.auth.dto.LoginRequest;
import com.synchat.backend.auth.dto.LoginResponse;
import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.dto.RefreshSessionRequest;
import com.synchat.backend.auth.dto.RefreshSessionResponse;
import com.synchat.backend.auth.dto.SuccessResponse;
import com.synchat.backend.auth.dto.UserProfileResponse;
import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.common.config.AppProperties;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.device.entity.DeviceEntity;
import com.synchat.backend.device.repository.DeviceRepository;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserEventOffsetRepository userEventOffsetRepository;
    private final DeviceRepository deviceRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final AppProperties appProperties;

    public AuthService(
            UserRepository userRepository,
            UserCredentialRepository userCredentialRepository,
            UserEventOffsetRepository userEventOffsetRepository,
            DeviceRepository deviceRepository,
            AuthSessionRepository authSessionRepository,
            PasswordService passwordService,
            RefreshTokenService refreshTokenService,
            AccessTokenService accessTokenService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.userEventOffsetRepository = userEventOffsetRepository;
        this.deviceRepository = deviceRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.appProperties = appProperties;
    }

    @Transactional
    public SuccessResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();

        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already used.");
        }

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String passwordHash = passwordService.hash(request.password());

        userRepository.save(UserEntity.create(userId, email, displayName, null, now));
        userCredentialRepository.save(UserCredentialEntity.create(userId, passwordHash, now));
        userEventOffsetRepository.save(UserEventOffsetEntity.create(userId, now));

        return new SuccessResponse(true);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);

        UserCredentialEntity credential = userCredentialRepository.findById(user.getId())
                .orElseThrow(this::invalidCredentials);

        if (!passwordService.matches(request.password(), credential.getPasswordHash())) {
            throw invalidCredentials();
        }

        deviceRepository.findById(request.deviceId())
                .ifPresentOrElse(
                        device -> device.updateLoginOwnerAndPlatform(user.getId(), request.platform(), now),
                        () -> deviceRepository.save(DeviceEntity.create(
                                request.deviceId(),
                                user.getId(),
                                request.platform(),
                                null,
                                null,
                                now
                        ))
                );

        UUID sessionId = UUID.randomUUID();
        String refreshToken = refreshTokenService.generateRefreshToken();
        String refreshTokenHash = refreshTokenService.hashRefreshToken(refreshToken);
        Instant refreshTokenExpiresAt = now.plus(appProperties.jwt().refreshTtlDays(), ChronoUnit.DAYS);

        authSessionRepository.save(AuthSessionEntity.create(
                sessionId,
                user.getId(),
                request.deviceId(),
                refreshTokenHash,
                now,
                refreshTokenExpiresAt
        ));

        AccessTokenService.AccessTokenResult accessToken = accessTokenService.createAccessToken(
                user.getId(),
                sessionId,
                request.deviceId()
        );

        return new LoginResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken,
                refreshTokenExpiresAt,
                new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl())
        );
    }

    @Transactional
    public RefreshSessionResponse refresh(RefreshSessionRequest request) {
        Instant now = Instant.now();
        String refreshTokenHash = refreshTokenService.hashRefreshToken(request.refreshToken());

        AuthSessionEntity session = authSessionRepository
                .findByRefreshTokenHashAndDeviceId(refreshTokenHash, request.deviceId())
                .orElseThrow(this::invalidRefreshSession);

        if (!session.isActiveAt(now)) {
            throw invalidRefreshSession();
        }

        String newRefreshToken = refreshTokenService.generateRefreshToken();
        String newRefreshTokenHash = refreshTokenService.hashRefreshToken(newRefreshToken);
        Instant newRefreshTokenExpiresAt = now.plus(appProperties.jwt().refreshTtlDays(), ChronoUnit.DAYS);
        session.rotateRefreshToken(newRefreshTokenHash, newRefreshTokenExpiresAt);

        AccessTokenService.AccessTokenResult accessToken = accessTokenService.createAccessToken(
                session.getUserId(),
                session.getId(),
                session.getDeviceId()
        );

        return new RefreshSessionResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                newRefreshToken,
                newRefreshTokenExpiresAt
        );
    }

    private AppException invalidCredentials() {
        return new AppException(ErrorCode.INVALID_CREDENTIALS, "Login email or password is incorrect.");
    }

    private AppException invalidRefreshSession() {
        return new AppException(ErrorCode.UNAUTHORIZED, "Invalid refresh session.");
    }
}
