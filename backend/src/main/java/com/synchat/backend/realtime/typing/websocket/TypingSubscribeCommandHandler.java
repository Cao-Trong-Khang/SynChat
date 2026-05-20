package com.synchat.backend.realtime.typing.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.typing.dto.TypingSubscribeCommandPayload;
import com.synchat.backend.realtime.typing.service.TypingService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TypingSubscribeCommandHandler implements WebSocketCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TypingSubscribeCommandHandler.class);

    private final ObjectMapper objectMapper;
    private final TypingService typingService;

    public TypingSubscribeCommandHandler(ObjectMapper objectMapper, TypingService typingService) {
        this.objectMapper = objectMapper;
        this.typingService = typingService;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.TYPING_SUBSCRIBE;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        TypingSubscribeCommandPayload commandPayload = convert(context, payload, TypingSubscribeCommandPayload.class);
        typingService.subscribe(
                context.binding().socketId(),
                context.binding().userId(),
                commandPayload.conversationId()
        );
    }

    private <T> T convert(WebSocketCommandContext context, JsonNode payload, Class<T> payloadType) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (Exception exception) {
            log.warn(
                    "TypingSubscribeCommandHandler.convert exception commandType={} requestId={} socketId={} userId={}",
                    commandType(),
                    context.requestId(),
                    context.binding().socketId(),
                    context.binding().userId(),
                    exception
            );
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }
    }
}
