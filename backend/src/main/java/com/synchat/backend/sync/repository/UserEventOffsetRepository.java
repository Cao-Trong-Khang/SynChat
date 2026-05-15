package com.synchat.backend.sync.repository;

import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventOffsetRepository extends JpaRepository<UserEventOffsetEntity, UUID> {
}
