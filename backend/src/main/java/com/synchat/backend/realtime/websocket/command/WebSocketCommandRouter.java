package com.synchat.backend.realtime.websocket.command;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.websocket.dto.WebSocketCommandEnvelope;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebSocketCommandRouter {

    private final Map<String, WebSocketCommandHandler> handlersByType;

    public WebSocketCommandRouter(List<WebSocketCommandHandler> handlers) {
        Map<String, WebSocketCommandHandler> mappedHandlers = new HashMap<>();
        for (WebSocketCommandHandler handler : handlers) {
            WebSocketCommandHandler existing = mappedHandlers.putIfAbsent(handler.commandType(), handler);
            if (existing != null) {
                throw new IllegalStateException("Duplicate websocket command handler: " + handler.commandType());
            }
        }
        this.handlersByType = Map.copyOf(mappedHandlers);
    }

    public void route(UserSessionBinding binding, WebSocketCommandEnvelope envelope) {
        if (envelope.type() == null || envelope.type().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Command type is required.");
        }

        WebSocketCommandHandler handler = handlersByType.get(envelope.type());
        if (handler == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unsupported command type.");
        }

        handler.handle(new WebSocketCommandContext(binding, envelope.requestId()), envelope.payload());
    }
}
