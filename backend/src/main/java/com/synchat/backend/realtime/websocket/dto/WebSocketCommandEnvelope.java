package com.synchat.backend.realtime.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record WebSocketCommandEnvelope(String type, String requestId, JsonNode payload) {
}
