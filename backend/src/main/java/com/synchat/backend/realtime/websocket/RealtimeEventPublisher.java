package com.synchat.backend.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.websocket.dto.WebSocketEventEnvelope;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketConnectionStore;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketConnectionStore connectionStore;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisher(
            WebSocketSessionRegistry sessionRegistry,
            WebSocketConnectionStore connectionStore,
            ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.connectionStore = connectionStore;
        this.objectMapper = objectMapper;
    }

    public int publishToUser(UUID userId, Long eventSeq, String eventType, Object payload) {
        String json = serializeEvent(eventSeq, eventType, payload);
        int sentCount = 0;

        for (UserSessionBinding binding : sessionRegistry.findByUserId(userId)) {
            WebSocketSession session = connectionStore.findBySocketId(binding.socketId()).orElse(null);
            if (session == null || !session.isOpen()) {
                continue;
            }

            try {
                session.sendMessage(new TextMessage(json));
                sentCount++;
            } catch (Exception exception) {
                log.warn(
                        "RealtimeEventPublisher.publishToUser exception commandType={} requestId=null socketId={} userId={}",
                        eventType,
                        binding.socketId(),
                        binding.userId(),
                        exception
                );
            }
        }

        return sentCount;
    }

    private String serializeEvent(Long eventSeq, String eventType, Object payload) {
        try {
            WebSocketEventEnvelope envelope = new WebSocketEventEnvelope(
                    "event",
                    eventSeq,
                    eventType,
                    payload
            );
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            log.warn(
                    "RealtimeEventPublisher.serializeEvent exception commandType={} requestId=null socketId=null userId=null",
                    eventType,
                    exception
            );
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to serialize realtime event.");
        }
    }
}
