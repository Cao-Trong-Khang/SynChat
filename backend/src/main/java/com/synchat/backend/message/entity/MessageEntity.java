package com.synchat.backend.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private Long seq;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at_server", nullable = false)
    private Instant createdAtServer;

    protected MessageEntity() {
    }

    public MessageEntity(
            UUID id,
            UUID conversationId,
            Long seq,
            UUID senderId,
            UUID clientMessageId,
            String body,
            Instant createdAtServer
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.seq = seq;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.body = body;
        this.createdAtServer = createdAtServer;
    }

    public static MessageEntity create(
            UUID id,
            UUID conversationId,
            Long seq,
            UUID senderId,
            UUID clientMessageId,
            String body,
            Instant createdAtServer
    ) {
        return new MessageEntity(id, conversationId, seq, senderId, clientMessageId, body, createdAtServer);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAtServer() {
        return createdAtServer;
    }
}
