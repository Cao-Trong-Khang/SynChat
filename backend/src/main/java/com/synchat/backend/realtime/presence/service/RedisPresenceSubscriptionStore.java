package com.synchat.backend.realtime.presence.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPresenceSubscriptionStore implements PresenceSubscriptionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceSubscriptionStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisPresenceSubscriptionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void subscribe(String socketId, Collection<UUID> targetUserIds) {
        try {
            if (targetUserIds == null) {
                return;
            }
            for (UUID targetUserId : targetUserIds) {
                if (targetUserId == null) {
                    continue;
                }
                redisTemplate.opsForSet().add(watchersKey(targetUserId), socketId);
                redisTemplate.opsForSet().add(subscriptionsKey(socketId), targetUserId.toString());
            }
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to store presence subscription.");
        }
    }

    @Override
    public void unsubscribe(String socketId, Collection<UUID> targetUserIds) {
        try {
            if (targetUserIds == null) {
                return;
            }
            for (UUID targetUserId : targetUserIds) {
                if (targetUserId == null) {
                    continue;
                }
                String watchersKey = watchersKey(targetUserId);
                redisTemplate.opsForSet().remove(watchersKey, socketId);
                redisTemplate.opsForSet().remove(subscriptionsKey(socketId), targetUserId.toString());
                deleteIfEmpty(watchersKey);
            }
            deleteIfEmpty(subscriptionsKey(socketId));
        } catch (Exception exception) {
            log.warn("Failed to remove presence subscription.", exception);
        }
    }

    @Override
    public void unsubscribeAll(String socketId) {
        try {
            String subscriptionsKey = subscriptionsKey(socketId);
            Set<String> targetUserIds = redisTemplate.opsForSet().members(subscriptionsKey);
            if (targetUserIds != null) {
                for (String targetUserId : targetUserIds) {
                    String watchersKey = watchersKey(targetUserId);
                    redisTemplate.opsForSet().remove(watchersKey, socketId);
                    deleteIfEmpty(watchersKey);
                }
            }
            redisTemplate.delete(subscriptionsKey);
        } catch (Exception exception) {
            log.warn("Failed to remove all presence subscriptions.", exception);
        }
    }

    @Override
    public Set<String> findWatcherSocketIds(UUID targetUserId) {
        try {
            Set<String> socketIds = redisTemplate.opsForSet().members(watchersKey(targetUserId));
            return socketIds == null ? Set.of() : socketIds;
        } catch (Exception exception) {
            log.warn("Failed to read presence watcher socket ids.", exception);
            return Set.of();
        }
    }

    private void deleteIfEmpty(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        if (size != null && size == 0) {
            redisTemplate.delete(key);
        }
    }

    private String watchersKey(UUID targetUserId) {
        return watchersKey(targetUserId.toString());
    }

    private String watchersKey(String targetUserId) {
        return "presence_watchers:" + targetUserId;
    }

    private String subscriptionsKey(String socketId) {
        return "presence_subscriptions:" + socketId;
    }
}
