package com.synchat.backend.realtime.websocket.session;

import java.util.Optional;
import org.springframework.web.socket.WebSocketSession;

public interface WebSocketConnectionStore {

    void register(WebSocketSession session);

    void unregister(String socketId);

    Optional<WebSocketSession> findBySocketId(String socketId);
}
