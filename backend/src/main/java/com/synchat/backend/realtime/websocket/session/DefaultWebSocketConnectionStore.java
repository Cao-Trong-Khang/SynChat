package com.synchat.backend.realtime.websocket.session;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DefaultWebSocketConnectionStore implements WebSocketConnectionStore {

    private final ConcurrentHashMap<String, WebSocketSession> sessionsBySocketId = new ConcurrentHashMap<>();

    @Override
    public void register(WebSocketSession session) {
        sessionsBySocketId.put(session.getId(), session);
    }

    @Override
    public void unregister(String socketId) {
        sessionsBySocketId.remove(socketId);
    }

    @Override
    public Optional<WebSocketSession> findBySocketId(String socketId) {
        return Optional.ofNullable(sessionsBySocketId.get(socketId));
    }
}
