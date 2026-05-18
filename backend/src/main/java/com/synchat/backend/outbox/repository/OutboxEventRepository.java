package com.synchat.backend.outbox.repository;

import com.synchat.backend.outbox.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from OutboxEventEntity o
            where o.status = :status
            order by o.createdAt asc
            """)
    List<OutboxEventEntity> findByStatusForUpdate(@Param("status") String status, Pageable pageable);

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(String status);
}
