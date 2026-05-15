package com.synchat.backend.auth.repository;

import com.synchat.backend.auth.entity.AuthSessionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    List<AuthSessionEntity> findByUserId(UUID userId);

    List<AuthSessionEntity> findByDeviceId(UUID deviceId);
}
