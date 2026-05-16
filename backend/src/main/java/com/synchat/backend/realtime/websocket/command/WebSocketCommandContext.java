package com.synchat.backend.realtime.websocket.command;

import com.synchat.backend.realtime.websocket.session.UserSessionBinding;

public record WebSocketCommandContext(UserSessionBinding binding, String requestId) {
}
