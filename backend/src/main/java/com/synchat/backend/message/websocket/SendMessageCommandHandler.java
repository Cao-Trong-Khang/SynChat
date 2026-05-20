package com.synchat.backend.message.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.message.dto.SendMessageCommandPayload;
import com.synchat.backend.message.service.MessageSendService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendMessageCommandHandler implements WebSocketCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SendMessageCommandHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageSendService messageSendService;

    public SendMessageCommandHandler(ObjectMapper objectMapper, MessageSendService messageSendService) {
        this.objectMapper = objectMapper;
        this.messageSendService = messageSendService;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.MESSAGES_SEND;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        SendMessageCommandPayload commandPayload;
        try {
            commandPayload = objectMapper.treeToValue(payload, SendMessageCommandPayload.class);
        } catch (Exception exception) {
            log.warn(
                    "SendMessageCommandHandler.handle exception commandType={} requestId={} socketId={} userId={}",
                    commandType(),
                    context.requestId(),
                    context.binding().socketId(),
                    context.binding().userId(),
                    exception
            );
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }

        messageSendService.send(commandPayload);
    }
}
