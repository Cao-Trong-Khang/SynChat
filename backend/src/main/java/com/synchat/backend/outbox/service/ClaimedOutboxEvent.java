package com.synchat.backend.outbox.service;

import java.util.UUID;

public record ClaimedOutboxEvent(UUID id, String eventType, String payload) {
}
