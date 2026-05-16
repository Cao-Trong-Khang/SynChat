package com.synchat.backend.realtime.websocket.dto;

public record WebSocketEventEnvelope(String type, Long eventSeq, String eventType, Object payload) {
}
