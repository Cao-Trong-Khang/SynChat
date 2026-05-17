package com.synchat.backend.sync.repository;

import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserEventOffsetRepository extends JpaRepository<UserEventOffsetEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from UserEventOffsetEntity o where o.userId = :userId")
    Optional<UserEventOffsetEntity> findByUserIdForUpdate(UUID userId);
}
