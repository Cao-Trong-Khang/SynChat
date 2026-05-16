package com.synchat.backend.sync.dto;

public record SyncEventItemResponse(Long eventSeq, String eventType, Object payload) {
}
