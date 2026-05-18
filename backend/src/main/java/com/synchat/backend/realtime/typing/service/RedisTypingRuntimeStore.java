package com.synchat.backend.realtime.typing.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTypingRuntimeStore implements TypingRuntimeStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTypingRuntimeStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTypingRuntimeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void startTyping(String socketId, UUID conversationId, UUID userId, Duration ttl) {
        try {
            String entry = encodeEntry(conversationId, userId);
            redisTemplate.opsForValue().set(typingKey(conversationId, userId), "1", ttl);
            redisTemplate.opsForSet().add(typingSocketsKey(conversationId, userId), socketId);
            redisTemplate.opsForSet().add(typingActiveKey(socketId), entry);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to store typing state.");
        }
    }

    @Override
    public boolean stopTyping(String socketId, UUID conversationId, UUID userId) {
        try {
            String typingSocketsKey = typingSocketsKey(conversationId, userId);
            redisTemplate.opsForSet().remove(typingSocketsKey, socketId);
            redisTemplate.opsForSet().remove(typingActiveKey(socketId), encodeEntry(conversationId, userId));

            Long size = redisTemplate.opsForSet().size(typingSocketsKey);
            if (size != null && size == 0) {
                redisTemplate.delete(typingSocketsKey);
                redisTemplate.delete(typingKey(conversationId, userId));
                return true;
            }
            return false;
        } catch (Exception exception) {
            log.warn("Failed to stop typing state.", exception);
            return false;
        }
    }

    @Override
    public Set<ActiveTypingEntry> findActiveTypingEntriesBySocketId(String socketId) {
        try {
            Set<String> entries = redisTemplate.opsForSet().members(typingActiveKey(socketId));
            if (entries == null) {
                return Set.of();
            }
            return entries.stream()
                    .map(this::parseEntry)
                    .filter(entry -> entry != null)
                    .collect(Collectors.toSet());
        } catch (Exception exception) {
            log.warn("Failed to read active typing entries.", exception);
            return Set.of();
        }
    }

    @Override
    public boolean isTyping(UUID conversationId, UUID userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(typingKey(conversationId, userId)));
        } catch (Exception exception) {
            log.warn("Failed to read typing state.", exception);
            return false;
        }
    }

    @Override
    public boolean expireIfInactive(UUID conversationId, UUID userId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(typingKey(conversationId, userId)))) {
                return false;
            }
            redisTemplate.delete(typingSocketsKey(conversationId, userId));
            return true;
        } catch (Exception exception) {
            log.warn("Failed to expire typing state.", exception);
            return false;
        }
    }

    private ActiveTypingEntry parseEntry(String entry) {
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ActiveTypingEntry(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String encodeEntry(UUID conversationId, UUID userId) {
        return conversationId + ":" + userId;
    }

    private String typingKey(UUID conversationId, UUID userId) {
        return "typing:" + conversationId + ":" + userId;
    }

    private String typingSocketsKey(UUID conversationId, UUID userId) {
        return "typing_sockets:" + conversationId + ":" + userId;
    }

    private String typingActiveKey(String socketId) {
        return "typing_active:" + socketId;
    }
}
