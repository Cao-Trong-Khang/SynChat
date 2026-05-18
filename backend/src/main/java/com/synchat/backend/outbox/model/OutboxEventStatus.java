package com.synchat.backend.outbox.model;

public final class OutboxEventStatus {

    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String PUBLISHED = "published";
    public static final String FAILED = "failed";

    private OutboxEventStatus() {
    }
}
