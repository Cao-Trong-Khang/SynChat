package com.synchat.backend.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.websocket.dto.WebSocketErrorEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketErrorSender {

    private static final Logger log = LoggerFactory.getLogger(WebSocketErrorSender.class);

    private final ObjectMapper objectMapper;

    public WebSocketErrorSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendError(WebSocketSession session, String requestId, ErrorCode code, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            WebSocketErrorEnvelope envelope = WebSocketErrorEnvelope.of(
                    requestId,
                    code.name(),
                    normalizeMessage(message)
            );
            String json = objectMapper.writeValueAsString(envelope);
            session.sendMessage(new TextMessage(json));
        } catch (Exception exception) {
            log.warn("Failed to send websocket error envelope.", exception);
        }
    }

    public void sendError(WebSocketSession session, String requestId, AppException exception) {
        sendError(session, requestId, exception.getCode(), exception.getMessage());
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Invalid command.";
        }
        return message;
    }
}
