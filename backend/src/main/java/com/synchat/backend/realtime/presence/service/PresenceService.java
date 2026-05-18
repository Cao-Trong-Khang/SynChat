package com.synchat.backend.realtime.presence.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.presence.dto.PresenceChangedPayload;
import com.synchat.backend.realtime.presence.dto.PresenceItemResponse;
import com.synchat.backend.realtime.presence.dto.PresenceSnapshotPayload;
import com.synchat.backend.realtime.presence.model.PresenceEventTypes;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import com.synchat.backend.realtime.websocket.session.WebSocketSessionRegistry;
import com.synchat.backend.user.entity.UserEntity;
import com.synchat.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final int MAX_SUBSCRIPTION_USER_IDS = 100;

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final PresenceSubscriptionStore presenceSubscriptionStore;
    private final EphemeralWebSocketEventSender ephemeralWebSocketEventSender;
    private final UserRepository userRepository;

    public PresenceService(
            WebSocketSessionRegistry webSocketSessionRegistry,
            PresenceSubscriptionStore presenceSubscriptionStore,
            EphemeralWebSocketEventSender ephemeralWebSocketEventSender,
            UserRepository userRepository
    ) {
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.presenceSubscriptionStore = presenceSubscriptionStore;
        this.ephemeralWebSocketEventSender = ephemeralWebSocketEventSender;
        this.userRepository = userRepository;
    }

    public void handleSocketConnected(UserSessionBinding binding, boolean wasUserOnlineBeforeConnect) {
        if (binding == null) {
            return;
        }
        if (!wasUserOnlineBeforeConnect) {
            notifyPresenceChanged(binding.userId(), true, null);
        }
    }

    @Transactional
    public void handleSocketDisconnected(UserSessionBinding binding) {
        if (binding == null) {
            return;
        }

        presenceSubscriptionStore.unsubscribeAll(binding.socketId());
        if (!webSocketSessionRegistry.findByUserId(binding.userId()).isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        userRepository.findById(binding.userId()).ifPresent(user -> {
            user.markLastSeenAt(now);
            userRepository.save(user);
        });
        notifyPresenceChanged(binding.userId(), false, now);
    }

    @Transactional(readOnly = true)
    public PresenceSnapshotPayload subscribe(String socketId, Collection<UUID> userIds) {
        validateSocketId(socketId);
        List<UUID> requestedUserIds = normalizeRequiredUserIds(userIds);
        Map<UUID, UserEntity> usersById = loadUsersById(requestedUserIds);

        presenceSubscriptionStore.subscribe(socketId, requestedUserIds);
        return buildSnapshot(requestedUserIds, usersById);
    }

    public void unsubscribe(String socketId, Collection<UUID> userIds) {
        validateSocketId(socketId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        presenceSubscriptionStore.unsubscribe(socketId, normalizeUserIds(userIds));
    }

    private PresenceSnapshotPayload buildSnapshot(List<UUID> userIds, Map<UUID, UserEntity> usersById) {
        List<PresenceItemResponse> items = new ArrayList<>();
        for (UUID userId : userIds) {
            boolean online = !webSocketSessionRegistry.findByUserId(userId).isEmpty();
            UserEntity user = usersById.get(userId);
            items.add(new PresenceItemResponse(
                    userId,
                    online,
                    online ? null : user.getLastSeenAt()
            ));
        }
        return new PresenceSnapshotPayload(items);
    }

    private void notifyPresenceChanged(UUID targetUserId, boolean online, Instant lastSeenAt) {
        Set<String> socketIds = presenceSubscriptionStore.findWatcherSocketIds(targetUserId);
        PresenceChangedPayload payload = new PresenceChangedPayload(targetUserId, online, lastSeenAt);
        for (String socketId : socketIds) {
            try {
                ephemeralWebSocketEventSender.sendToSocket(
                        socketId,
                        PresenceEventTypes.PRESENCE_CHANGED,
                        null,
                        payload
                );
            } catch (Exception exception) {
                log.warn("Failed to send presence changed event.", exception);
            }
        }
    }

    private Map<UUID, UserEntity> loadUsersById(List<UUID> userIds) {
        Map<UUID, UserEntity> usersById = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        if (usersById.size() != userIds.size()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Requested resource not found.");
        }
        return usersById;
    }

    private List<UUID> normalizeRequiredUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw validation("Presence subscription userIds are required.");
        }
        List<UUID> normalized = normalizeUserIds(userIds);
        if (normalized.isEmpty()) {
            throw validation("Presence subscription userIds are required.");
        }
        if (normalized.size() > MAX_SUBSCRIPTION_USER_IDS) {
            throw validation("Too many presence subscription userIds.");
        }
        return normalized;
    }

    private List<UUID> normalizeUserIds(Collection<UUID> userIds) {
        return new ArrayList<>(new LinkedHashSet<>(userIds));
    }

    private void validateSocketId(String socketId) {
        if (socketId == null || socketId.isBlank()) {
            throw validation("socketId is required.");
        }
    }

    private AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
}
