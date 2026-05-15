package com.synchat.backend.auth.service;

import com.synchat.backend.auth.dto.RegisterRequest;
import com.synchat.backend.auth.dto.SuccessResponse;
import com.synchat.backend.auth.entity.UserCredentialEntity;
import com.synchat.backend.auth.repository.UserCredentialRepository;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserEventOffsetRepository userEventOffsetRepository;
    private final PasswordService passwordService;

    public AuthService(
            UserRepository userRepository,
            UserCredentialRepository userCredentialRepository,
            UserEventOffsetRepository userEventOffsetRepository,
            PasswordService passwordService
    ) {
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.userEventOffsetRepository = userEventOffsetRepository;
        this.passwordService = passwordService;
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
}
