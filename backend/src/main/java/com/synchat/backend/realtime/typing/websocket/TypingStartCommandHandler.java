package com.synchat.backend.realtime.typing.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.typing.dto.TypingStartCommandPayload;
import com.synchat.backend.realtime.typing.service.TypingService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.springframework.stereotype.Component;

@Component
public class TypingStartCommandHandler implements WebSocketCommandHandler {

    private final ObjectMapper objectMapper;
    private final TypingService typingService;

    public TypingStartCommandHandler(ObjectMapper objectMapper, TypingService typingService) {
        this.objectMapper = objectMapper;
        this.typingService = typingService;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.TYPING_START;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        TypingStartCommandPayload commandPayload = convert(payload, TypingStartCommandPayload.class);
        typingService.startTyping(context.binding(), commandPayload.conversationId());
    }

    private <T> T convert(JsonNode payload, Class<T> payloadType) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }
    }
}
