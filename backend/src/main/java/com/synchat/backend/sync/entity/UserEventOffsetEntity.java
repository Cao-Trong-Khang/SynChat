package com.synchat.backend.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_event_offsets")
public class UserEventOffsetEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_event_seq", nullable = false)
    private Long lastEventSeq;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEventOffsetEntity() {
    }

    public UserEventOffsetEntity(UUID userId, Long lastEventSeq, Instant updatedAt) {
        this.userId = userId;
        this.lastEventSeq = lastEventSeq;
        this.updatedAt = updatedAt;
    }

    public static UserEventOffsetEntity create(UUID userId, Instant now) {
        return new UserEventOffsetEntity(userId, 0L, now);
    }

    public Long incrementAndGet() {
        return incrementAndGet(Instant.now());
    }

    public Long incrementAndGet(Instant now) {
        this.lastEventSeq += 1;
        this.updatedAt = now;
        return this.lastEventSeq;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getLastEventSeq() {
        return lastEventSeq;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
