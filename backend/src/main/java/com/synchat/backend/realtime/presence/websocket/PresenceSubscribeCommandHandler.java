package com.synchat.backend.realtime.presence.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.presence.dto.PresenceSnapshotPayload;
import com.synchat.backend.realtime.presence.dto.PresenceSubscribeCommandPayload;
import com.synchat.backend.realtime.presence.model.PresenceEventTypes;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.springframework.stereotype.Component;

@Component
public class PresenceSubscribeCommandHandler implements WebSocketCommandHandler {

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;
    private final EphemeralWebSocketEventSender ephemeralWebSocketEventSender;

    public PresenceSubscribeCommandHandler(
            ObjectMapper objectMapper,
            PresenceService presenceService,
            EphemeralWebSocketEventSender ephemeralWebSocketEventSender
    ) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
        this.ephemeralWebSocketEventSender = ephemeralWebSocketEventSender;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.PRESENCE_SUBSCRIBE;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        PresenceSubscribeCommandPayload commandPayload;
        try {
            commandPayload = objectMapper.treeToValue(payload, PresenceSubscribeCommandPayload.class);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }

        PresenceSnapshotPayload snapshot = presenceService.subscribe(
                context.binding().socketId(),
                commandPayload.userIds()
        );
        ephemeralWebSocketEventSender.sendToSocket(
                context.binding().socketId(),
                PresenceEventTypes.PRESENCE_SNAPSHOT,
                context.requestId(),
                snapshot
        );
    }
}
