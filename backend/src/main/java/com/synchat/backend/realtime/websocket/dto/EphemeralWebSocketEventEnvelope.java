package com.synchat.backend.realtime.websocket.dto;

public record EphemeralWebSocketEventEnvelope(String type, String requestId, Object payload) {
}
