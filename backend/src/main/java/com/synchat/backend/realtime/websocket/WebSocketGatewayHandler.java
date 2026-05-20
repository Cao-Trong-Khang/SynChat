package com.synchat.backend.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import com.synchat.backend.realtime.presence.service.PresenceService;
import com.synchat.backend.realtime.typing.service.TypingService;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandRouter;
import com.synchat.backend.realtime.websocket.dto.WebSocketCommandEnvelope;
import com.synchat.backend.realtime.websocket.security.WebSocketAuthAttributes;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketConnectionStore;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WebSocketGatewayHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketGatewayHandler.class);

    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketConnectionStore connectionStore;
    private final WebSocketCommandRouter commandRouter;
    private final WebSocketErrorSender errorSender;
    private final PresenceService presenceService;
    private final TypingService typingService;

    public WebSocketGatewayHandler(
            ObjectMapper objectMapper,
            WebSocketSessionRegistry sessionRegistry,
            WebSocketConnectionStore connectionStore,
            WebSocketCommandRouter commandRouter,
            WebSocketErrorSender errorSender,
            PresenceService presenceService,
            TypingService typingService
    ) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.connectionStore = connectionStore;
        this.commandRouter = commandRouter;
        this.errorSender = errorSender;
        this.presenceService = presenceService;
        this.typingService = typingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object principalAttribute = session.getAttributes().get(WebSocketAuthAttributes.AUTH_PRINCIPAL);
        if (!(principalAttribute instanceof AuthPrincipal principal)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        boolean wasUserOnline = !sessionRegistry.findByUserId(principal.userId()).isEmpty();
        connectionStore.register(session);
        sessionRegistry.register(session.getId(), principal);
        sessionRegistry.findBySocketId(session.getId())
                .ifPresent(binding -> presenceService.handleSocketConnected(binding, wasUserOnline));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketCommandEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayload(), WebSocketCommandEnvelope.class);
        } catch (Exception exception) {
            log.warn(
                    "WebSocketGatewayHandler.handleTextMessage exception commandType=unknown requestId=null socketId={} userId=null",
                    session.getId(),
                    exception
            );
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
            routeAuthenticated(binding.get(), envelope);
        } catch (AppException exception) {
            log.warn(
                    "WebSocketGatewayHandler.handleTextMessage exception commandType={} requestId={} socketId={} userId={}",
                    envelope.type(),
                    envelope.requestId(),
                    binding.get().socketId(),
                    binding.get().userId(),
                    exception
            );
            errorSender.sendError(session, envelope.requestId(), exception);
        } catch (Exception exception) {
            log.warn(
                    "WebSocketGatewayHandler.handleTextMessage exception commandType={} requestId={} socketId={} userId={}",
                    envelope.type(),
                    envelope.requestId(),
                    binding.get().socketId(),
                    binding.get().userId(),
                    exception
            );
            errorSender.sendError(
                    session,
                    envelope.requestId(),
                    ErrorCode.INTERNAL_ERROR,
                    "Unexpected server error."
            );
        }
    }

    private void routeAuthenticated(UserSessionBinding binding, WebSocketCommandEnvelope envelope) {
        AuthPrincipal principal = new AuthPrincipal(binding.userId(), binding.sessionId(), binding.deviceId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            commandRouter.route(binding, envelope);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Optional<UserSessionBinding> binding = sessionRegistry.findBySocketId(session.getId());
        sessionRegistry.unregister(session.getId());
        connectionStore.unregister(session.getId());
        cleanupDisconnectedBinding(binding);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Optional<UserSessionBinding> binding = sessionRegistry.findBySocketId(session.getId());
        sessionRegistry.unregister(session.getId());
        connectionStore.unregister(session.getId());
        cleanupDisconnectedBinding(binding);
        closeIfOpen(session);
    }

    private void cleanupDisconnectedBinding(Optional<UserSessionBinding> binding) {
        binding.ifPresent(value -> {
            try {
                typingService.handleSocketDisconnected(value);
            } catch (Exception exception) {
                log.warn(
                        "WebSocketGatewayHandler.cleanupDisconnectedBinding exception commandType=unknown requestId=null socketId={} userId={}",
                        value.socketId(),
                        value.userId(),
                        exception
                );
            }
            presenceService.handleSocketDisconnected(value);
        });
    }

    private void closeIfOpen(WebSocketSession session) throws IOException {
        if (session.isOpen()) {
            session.close();
        }
    }
}
