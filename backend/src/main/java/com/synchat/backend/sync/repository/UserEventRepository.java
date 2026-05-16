package com.synchat.backend.sync.repository;

import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserEventRepository extends JpaRepository<UserEventEntity, UserEventId> {

    List<UserEventEntity> findByUserIdAndEventSeqGreaterThanOrderByEventSeqAsc(UUID userId, Long afterSeq);

    List<UserEventEntity> findByUserIdAndEventSeqGreaterThanOrderByEventSeqAsc(
            UUID userId,
            Long eventSeq,
            Pageable pageable
    );

    @Query("select min(e.eventSeq) from UserEventEntity e where e.userId = :userId")
    Optional<Long> findMinEventSeqByUserId(UUID userId);

    long countByUserIdAndEventSeqGreaterThan(UUID userId, Long eventSeq);
}
