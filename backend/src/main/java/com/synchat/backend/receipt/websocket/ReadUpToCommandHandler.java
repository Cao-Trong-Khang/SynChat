package com.synchat.backend.receipt.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.receipt.dto.ReadUpToCommandPayload;
import com.synchat.backend.receipt.service.ReceiptService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.springframework.stereotype.Component;

@Component
public class ReadUpToCommandHandler implements WebSocketCommandHandler {

    private final ObjectMapper objectMapper;
    private final ReceiptService receiptService;

    public ReadUpToCommandHandler(ObjectMapper objectMapper, ReceiptService receiptService) {
        this.objectMapper = objectMapper;
        this.receiptService = receiptService;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.READ_UP_TO;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        ReadUpToCommandPayload commandPayload;
        try {
            commandPayload = objectMapper.treeToValue(payload, ReadUpToCommandPayload.class);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }

        receiptService.readUpTo(commandPayload);
    }
}
