package com.synchat.backend.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(UserEventId.class)
@Table(name = "user_events")
public class UserEventEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "event_seq")
    private Long eventSeq;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEventEntity() {
    }

    public UserEventEntity(UUID userId, Long eventSeq, String eventType, String payload, Instant createdAt) {
        this.userId = userId;
        this.eventSeq = eventSeq;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public static UserEventEntity create(
            UUID userId,
            Long eventSeq,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        return new UserEventEntity(userId, eventSeq, eventType, payload, createdAt);
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getEventSeq() {
        return eventSeq;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
