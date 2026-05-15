package com.synchat.backend.conversation.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ConversationMemberId implements Serializable {

    private UUID conversationId;
    private UUID userId;

    public ConversationMemberId() {
    }

    public ConversationMemberId(UUID conversationId, UUID userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConversationMemberId that)) {
            return false;
        }

        return Objects.equals(conversationId, that.conversationId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, userId);
    }
}
