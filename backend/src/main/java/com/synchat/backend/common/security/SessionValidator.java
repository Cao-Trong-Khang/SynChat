package com.synchat.backend.common.security;

import com.synchat.backend.auth.entity.AuthSessionEntity;
import com.synchat.backend.auth.repository.AuthSessionRepository;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SessionValidator {

    private final AuthSessionRepository authSessionRepository;

    public SessionValidator(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    public void validateActiveSession(AuthPrincipal principal) {
        AuthSessionEntity session = authSessionRepository.findById(principal.sessionId())
                .orElseThrow(this::unauthorized);

        if (!session.getUserId().equals(principal.userId())
                || !session.getDeviceId().equals(principal.deviceId())
                || !session.isActiveAt(Instant.now())) {
            throw unauthorized();
        }
    }

    private AppException unauthorized() {
        return new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized.");
    }
}
