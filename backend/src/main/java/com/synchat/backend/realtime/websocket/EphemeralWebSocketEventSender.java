package com.synchat.backend.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.websocket.dto.EphemeralWebSocketEventEnvelope;
import com.synchat.backend.realtime.websocket.session.WebSocketConnectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class EphemeralWebSocketEventSender {

    private static final Logger log = LoggerFactory.getLogger(EphemeralWebSocketEventSender.class);

    private final WebSocketConnectionStore connectionStore;
    private final ObjectMapper objectMapper;

    public EphemeralWebSocketEventSender(WebSocketConnectionStore connectionStore, ObjectMapper objectMapper) {
        this.connectionStore = connectionStore;
        this.objectMapper = objectMapper;
    }

    public void sendToSocket(String socketId, String type, String requestId, Object payload) {
        WebSocketSession session = connectionStore.findBySocketId(socketId).orElse(null);
        if (session == null || !session.isOpen()) {
            return;
        }

        String json = serializeEvent(type, requestId, payload);
        try {
            session.sendMessage(new TextMessage(json));
        } catch (Exception exception) {
            log.warn(
                    "EphemeralWebSocketEventSender.sendToSocket exception commandType={} requestId={} socketId={} userId=null",
                    type,
                    requestId,
                    socketId,
                    exception
            );
        }
    }

    private String serializeEvent(String type, String requestId, Object payload) {
        try {
            EphemeralWebSocketEventEnvelope envelope = new EphemeralWebSocketEventEnvelope(type, requestId, payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            log.warn(
                    "EphemeralWebSocketEventSender.serializeEvent exception commandType={} requestId={} socketId=null userId=null",
                    type,
                    requestId,
                    exception
            );
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to serialize websocket event.");
        }
    }
}
