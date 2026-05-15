package com.synchat.backend.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    private UUID id;

    @Column(name = "direct_key", nullable = false, length = 255)
    private String directKey;

    @Column(name = "last_message_seq", nullable = false)
    private Long lastMessageSeq;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(
            UUID id,
            String directKey,
            Long lastMessageSeq,
            Instant lastMessageAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.directKey = directKey;
        this.lastMessageSeq = lastMessageSeq;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDirectKey() {
        return directKey;
    }

    public Long getLastMessageSeq() {
        return lastMessageSeq;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
