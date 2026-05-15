package com.synchat.backend.auth.repository;

import com.synchat.backend.auth.entity.AuthSessionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    List<AuthSessionEntity> findByUserId(UUID userId);

    List<AuthSessionEntity> findByDeviceId(UUID deviceId);

    Optional<AuthSessionEntity> findByRefreshTokenHashAndDeviceId(String refreshTokenHash, UUID deviceId);

    List<AuthSessionEntity> findByUserIdAndDeviceId(UUID userId, UUID deviceId);
}
