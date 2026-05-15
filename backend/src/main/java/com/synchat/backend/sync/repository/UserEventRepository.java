package com.synchat.backend.sync.repository;

import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventRepository extends JpaRepository<UserEventEntity, UserEventId> {

    List<UserEventEntity> findByUserIdAndEventSeqGreaterThanOrderByEventSeqAsc(UUID userId, Long afterSeq);
}
