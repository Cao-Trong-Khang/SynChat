package com.synchat.backend.realtime.websocket.dto;

public record WebSocketErrorEnvelope(String type, String requestId, WebSocketError error) {

    public static WebSocketErrorEnvelope of(String requestId, String code, String message) {
        return new WebSocketErrorEnvelope("error", requestId, new WebSocketError(code, message));
    }
}
