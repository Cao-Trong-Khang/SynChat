package com.synchat.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_credentials")
public class UserCredentialEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCredentialEntity() {
    }

    public UserCredentialEntity(UUID userId, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserCredentialEntity create(UUID userId, String passwordHash, Instant now) {
        return new UserCredentialEntity(userId, passwordHash, now, now);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
