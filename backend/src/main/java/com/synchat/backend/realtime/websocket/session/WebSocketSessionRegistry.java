package com.synchat.backend.realtime.websocket.session;

import com.synchat.backend.common.security.AuthPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebSocketSessionRegistry {

    void register(String socketId, AuthPrincipal principal);

    void unregister(String socketId);

    Optional<UserSessionBinding> findBySocketId(String socketId);

    List<UserSessionBinding> findByUserId(UUID userId);

    List<UserSessionBinding> findByDeviceId(UUID deviceId);
}
