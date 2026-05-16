package com.synchat.backend.realtime.websocket.command;

import com.fasterxml.jackson.databind.JsonNode;

public interface WebSocketCommandHandler {

    String commandType();

    void handle(WebSocketCommandContext context, JsonNode payload);
}
