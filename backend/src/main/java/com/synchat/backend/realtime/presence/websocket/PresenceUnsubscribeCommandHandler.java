package com.synchat.backend.realtime.presence.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.presence.dto.PresenceUnsubscribeCommandPayload;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.springframework.stereotype.Component;

@Component
public class PresenceUnsubscribeCommandHandler implements WebSocketCommandHandler {

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;

    public PresenceUnsubscribeCommandHandler(ObjectMapper objectMapper, PresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.PRESENCE_UNSUBSCRIBE;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        PresenceUnsubscribeCommandPayload commandPayload;
        try {
            commandPayload = objectMapper.treeToValue(payload, PresenceUnsubscribeCommandPayload.class);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }

        presenceService.unsubscribe(context.binding().socketId(), commandPayload.userIds());
    }
}
