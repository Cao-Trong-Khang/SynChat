package com.synchat.backend.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "push_token")
    private String pushToken;

    @Column(name = "push_provider", length = 50)
    private String pushProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceEntity() {
    }

    public DeviceEntity(
            UUID id,
            UUID userId,
            String platform,
            String pushToken,
            String pushProvider,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.platform = platform;
        this.pushToken = pushToken;
        this.pushProvider = pushProvider;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPushToken() {
        return pushToken;
    }

    public String getPushProvider() {
        return pushProvider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
