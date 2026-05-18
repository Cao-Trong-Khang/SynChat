package com.synchat.backend.realtime.typing.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.conversation.repository.ConversationMemberRepository;
import com.synchat.backend.realtime.typing.dto.TypingEventPayload;
import com.synchat.backend.realtime.typing.model.TypingEventTypes;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class TypingService implements TypingExpirationNotifier {

    private static final Logger log = LoggerFactory.getLogger(TypingService.class);
    private static final Duration TYPING_TTL = Duration.ofSeconds(5);

    private final ConversationMemberRepository conversationMemberRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final EphemeralWebSocketEventSender ephemeralWebSocketEventSender;
    private final TypingSubscriptionStore typingSubscriptionStore;
    private final TypingRuntimeStore typingRuntimeStore;
    private final TypingExpirationScheduler typingExpirationScheduler;

    public TypingService(
            ConversationMemberRepository conversationMemberRepository,
            WebSocketSessionRegistry webSocketSessionRegistry,
            EphemeralWebSocketEventSender ephemeralWebSocketEventSender,
            TypingSubscriptionStore typingSubscriptionStore,
            TypingRuntimeStore typingRuntimeStore,
            @Lazy TypingExpirationScheduler typingExpirationScheduler
    ) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.ephemeralWebSocketEventSender = ephemeralWebSocketEventSender;
        this.typingSubscriptionStore = typingSubscriptionStore;
        this.typingRuntimeStore = typingRuntimeStore;
        this.typingExpirationScheduler = typingExpirationScheduler;
    }

    public void subscribe(String socketId, UUID userId, UUID conversationId) {
        validateMember(conversationId, userId);
        typingSubscriptionStore.subscribe(socketId, conversationId);
    }

    public void unsubscribe(String socketId, UUID conversationId) {
        if (conversationId == null) {
            throw validation("conversationId is required.");
        }
        typingSubscriptionStore.unsubscribe(socketId, conversationId);
    }

    public void startTyping(UserSessionBinding binding, UUID conversationId) {
        validateBinding(binding);
        validateMember(conversationId, binding.userId());
        typingRuntimeStore.startTyping(binding.socketId(), conversationId, binding.userId(), TYPING_TTL);
        typingExpirationScheduler.scheduleExpirationCheck(conversationId, binding.userId(), TYPING_TTL);
        sendTypingEvent(conversationId, binding.userId(), TypingEventTypes.TYPING_STARTED);
    }

    public void stopTyping(UserSessionBinding binding, UUID conversationId) {
        validateBinding(binding);
        validateMember(conversationId, binding.userId());
        if (typingRuntimeStore.stopTyping(binding.socketId(), conversationId, binding.userId())) {
            sendTypingEvent(conversationId, binding.userId(), TypingEventTypes.TYPING_STOPPED);
        }
    }

    public void handleSocketDisconnected(UserSessionBinding binding) {
        if (binding == null) {
            return;
        }

        Set<ActiveTypingEntry> entries = typingRuntimeStore.findActiveTypingEntriesBySocketId(binding.socketId());
        for (ActiveTypingEntry entry : entries) {
            if (!binding.userId().equals(entry.userId())) {
                continue;
            }
            if (typingRuntimeStore.stopTyping(binding.socketId(), entry.conversationId(), binding.userId())) {
                sendTypingEvent(entry.conversationId(), binding.userId(), TypingEventTypes.TYPING_STOPPED);
            }
        }

        typingSubscriptionStore.unsubscribeAll(binding.socketId());
    }

    @Override
    public void onTypingExpired(UUID conversationId, UUID userId) {
        sendTypingEvent(conversationId, userId, TypingEventTypes.TYPING_STOPPED);
    }

    private void sendTypingEvent(UUID conversationId, UUID typingUserId, String eventType) {
        TypingEventPayload payload = new TypingEventPayload(conversationId, typingUserId);
        for (String socketId : typingSubscriptionStore.findSubscriberSocketIds(conversationId)) {
            try {
                webSocketSessionRegistry.findBySocketId(socketId)
                        .filter(binding -> !binding.userId().equals(typingUserId))
                        .ifPresent(binding -> ephemeralWebSocketEventSender.sendToSocket(
                                socketId,
                                eventType,
                                null,
                                payload
                        ));
            } catch (Exception exception) {
                log.warn("Failed to send typing event.", exception);
            }
        }
    }

    private void validateBinding(UserSessionBinding binding) {
        if (binding == null) {
            throw validation("Authenticated websocket session is required.");
        }
        if (binding.userId() == null) {
            throw validation("userId is required.");
        }
    }

    private void validateMember(UUID conversationId, UUID userId) {
        if (conversationId == null) {
            throw validation("conversationId is required.");
        }
        if (userId == null) {
            throw validation("userId is required.");
        }
        if (conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId).isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
        }
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
}
