package com.synchat.backend.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandRouter;
import com.synchat.backend.realtime.websocket.dto.WebSocketCommandEnvelope;
import com.synchat.backend.realtime.websocket.security.WebSocketAuthAttributes;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WebSocketGatewayHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketCommandRouter commandRouter;
    private final WebSocketErrorSender errorSender;

    public WebSocketGatewayHandler(
            ObjectMapper objectMapper,
            WebSocketSessionRegistry sessionRegistry,
            WebSocketCommandRouter commandRouter,
            WebSocketErrorSender errorSender
    ) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.commandRouter = commandRouter;
        this.errorSender = errorSender;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object principalAttribute = session.getAttributes().get(WebSocketAuthAttributes.AUTH_PRINCIPAL);
        if (!(principalAttribute instanceof AuthPrincipal principal)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        sessionRegistry.register(session.getId(), principal);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketCommandEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayload(), WebSocketCommandEnvelope.class);
        } catch (Exception exception) {
            errorSender.sendError(
                    session,
                    null,
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid WebSocket message."
            );
            return;
        }

        Optional<UserSessionBinding> binding = sessionRegistry.findBySocketId(session.getId());
        if (binding.isEmpty()) {
            errorSender.sendError(session, envelope.requestId(), ErrorCode.UNAUTHORIZED, "Unauthorized.");
            return;
        }

        try {
            commandRouter.route(binding.get(), envelope);
        } catch (AppException exception) {
            errorSender.sendError(session, envelope.requestId(), exception);
        } catch (Exception exception) {
            errorSender.sendError(
                    session,
                    envelope.requestId(),
                    ErrorCode.INTERNAL_ERROR,
                    "Unexpected server error."
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessionRegistry.unregister(session.getId());
        closeIfOpen(session);
    }

    private void closeIfOpen(WebSocketSession session) throws IOException {
        if (session.isOpen()) {
            session.close();
        }
    }
}
