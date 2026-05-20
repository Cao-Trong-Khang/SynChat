package com.synchat.backend.realtime.websocket.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.security.AuthPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DefaultWebSocketSessionRegistry implements WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebSocketSessionRegistry.class);

    private final ConcurrentHashMap<String, UserSessionBinding> bindingsBySocketId = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DefaultWebSocketSessionRegistry(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(String socketId, AuthPrincipal principal) {
        UserSessionBinding binding = new UserSessionBinding(
                socketId,
                principal.userId(),
                principal.sessionId(),
                principal.deviceId(),
                Instant.now()
        );

        bindingsBySocketId.put(socketId, binding);
        writeRedisBinding(binding);
    }

    @Override
    public void unregister(String socketId) {
        UserSessionBinding binding = bindingsBySocketId.remove(socketId);
        if (binding == null) {
            return;
        }

        removeRedisBinding(binding);
    }

    @Override
    public Optional<UserSessionBinding> findBySocketId(String socketId) {
        return Optional.ofNullable(bindingsBySocketId.get(socketId));
    }

    @Override
    public List<UserSessionBinding> findByUserId(UUID userId) {
        return bindingsBySocketId.values()
                .stream()
                .filter(binding -> binding.userId().equals(userId))
                .toList();
    }

    @Override
    public List<UserSessionBinding> findByDeviceId(UUID deviceId) {
        return bindingsBySocketId.values()
                .stream()
                .filter(binding -> binding.deviceId().equals(deviceId))
                .toList();
    }

    private void writeRedisBinding(UserSessionBinding binding) {
        try {
            String socketId = binding.socketId();
            redisTemplate.opsForSet().add(sessionKey(binding.sessionId()), socketId);
            redisTemplate.opsForSet().add(userSessionsKey(binding.userId()), socketId);
            redisTemplate.opsForSet().add(deviceSessionKey(binding.deviceId()), socketId);
            redisTemplate.opsForValue().set(socketKey(socketId), objectMapper.writeValueAsString(binding));
        } catch (Exception exception) {
            log.warn(
                    "DefaultWebSocketSessionRegistry.writeRedisBinding exception commandType=registry.register requestId=null socketId={} userId={}",
                    binding.socketId(),
                    binding.userId(),
                    exception
            );
        }
    }

    private void removeRedisBinding(UserSessionBinding binding) {
        try {
            String socketId = binding.socketId();
            String sessionKey = sessionKey(binding.sessionId());
            String userSessionsKey = userSessionsKey(binding.userId());
            String deviceSessionKey = deviceSessionKey(binding.deviceId());

            redisTemplate.opsForSet().remove(sessionKey, socketId);
            redisTemplate.opsForSet().remove(userSessionsKey, socketId);
            redisTemplate.opsForSet().remove(deviceSessionKey, socketId);
            redisTemplate.delete(socketKey(socketId));

            deleteIfEmpty(sessionKey);
            deleteIfEmpty(userSessionsKey);
            deleteIfEmpty(deviceSessionKey);
        } catch (Exception exception) {
            log.warn(
                    "DefaultWebSocketSessionRegistry.removeRedisBinding exception commandType=registry.unregister requestId=null socketId={} userId={}",
                    binding.socketId(),
                    binding.userId(),
                    exception
            );
        }
    }

    private void deleteIfEmpty(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        if (size != null && size == 0) {
            redisTemplate.delete(key);
        }
    }

    private String sessionKey(UUID sessionId) {
        return "session:" + sessionId;
    }

    private String userSessionsKey(UUID userId) {
        return "user_sessions:" + userId;
    }

    private String deviceSessionKey(UUID deviceId) {
        return "device_session:" + deviceId;
    }

    private String socketKey(String socketId) {
        return "socket:" + socketId;
    }
}
