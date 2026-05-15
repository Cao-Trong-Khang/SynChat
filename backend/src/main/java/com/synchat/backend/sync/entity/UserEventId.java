package com.synchat.backend.sync.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserEventId implements Serializable {

    private UUID userId;
    private Long eventSeq;

    public UserEventId() {
    }

    public UserEventId(UUID userId, Long eventSeq) {
        this.userId = userId;
        this.eventSeq = eventSeq;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof UserEventId that)) {
            return false;
        }

        return Objects.equals(userId, that.userId)
                && Objects.equals(eventSeq, that.eventSeq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, eventSeq);
    }
}
