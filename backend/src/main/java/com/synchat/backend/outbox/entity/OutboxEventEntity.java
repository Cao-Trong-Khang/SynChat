package com.synchat.backend.outbox.entity;

import com.synchat.backend.outbox.model.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(UUID id, String eventType, String payload, String status, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static OutboxEventEntity create(
            UUID id,
            String eventType,
            String payload,
            String status,
            Instant createdAt
    ) {
        return new OutboxEventEntity(id, eventType, payload, status, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markProcessing() {
        this.status = OutboxEventStatus.PROCESSING;
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
    }

    public void markFailed() {
        this.status = OutboxEventStatus.FAILED;
    }
}
