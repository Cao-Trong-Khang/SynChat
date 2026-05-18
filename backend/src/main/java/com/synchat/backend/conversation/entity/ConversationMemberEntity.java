package com.synchat.backend.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(ConversationMemberId.class)
@Table(name = "conversation_members")
public class ConversationMemberEntity {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "read_seq", nullable = false)
    private Long readSeq;

    @Column(name = "delivered_seq", nullable = false)
    private Long deliveredSeq;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationMemberEntity() {
    }

    public ConversationMemberEntity(
            UUID conversationId,
            UUID userId,
            Long readSeq,
            Long deliveredSeq,
            Integer unreadCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.readSeq = readSeq;
        this.deliveredSeq = deliveredSeq;
        this.unreadCount = unreadCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ConversationMemberEntity create(UUID conversationId, UUID userId, Instant now) {
        return new ConversationMemberEntity(conversationId, userId, 0L, 0L, 0, now, now);
    }

    public void incrementUnread(Instant now) {
        this.unreadCount += 1;
        this.updatedAt = now;
    }

    public void clearUnread(Instant now) {
        this.unreadCount = 0;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public boolean updateDeliveredSeq(Long deliveredSeq, Instant now) {
        if (deliveredSeq == null) {
            return false;
        }
        if (this.deliveredSeq == null || deliveredSeq > this.deliveredSeq) {
            this.deliveredSeq = deliveredSeq;
            this.updatedAt = now;
            return true;
        }
        return false;
    }

    public boolean updateReadSeqAndClearUnread(Long readSeq, Instant now) {
        if (readSeq == null) {
            return false;
        }
        if (this.readSeq == null || readSeq > this.readSeq) {
            this.readSeq = readSeq;
            this.unreadCount = 0;
            this.updatedAt = now;
            return true;
        }
        if (this.unreadCount != null && this.unreadCount > 0) {
            this.unreadCount = 0;
            this.updatedAt = now;
            return true;
        }
        return false;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getReadSeq() {
        return readSeq;
    }

    public Long getDeliveredSeq() {
        return deliveredSeq;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
