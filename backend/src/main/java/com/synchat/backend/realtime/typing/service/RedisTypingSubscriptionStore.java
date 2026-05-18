package com.synchat.backend.realtime.typing.service;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTypingSubscriptionStore implements TypingSubscriptionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTypingSubscriptionStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTypingSubscriptionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void subscribe(String socketId, UUID conversationId) {
        try {
            redisTemplate.opsForSet().add(subscribersKey(conversationId), socketId);
            redisTemplate.opsForSet().add(subscriptionsKey(socketId), conversationId.toString());
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to store typing subscription.");
        }
    }

    @Override
    public void unsubscribe(String socketId, UUID conversationId) {
        try {
            String subscribersKey = subscribersKey(conversationId);
            String subscriptionsKey = subscriptionsKey(socketId);
            redisTemplate.opsForSet().remove(subscribersKey, socketId);
            redisTemplate.opsForSet().remove(subscriptionsKey, conversationId.toString());
            deleteIfEmpty(subscribersKey);
            deleteIfEmpty(subscriptionsKey);
        } catch (Exception exception) {
            log.warn("Failed to remove typing subscription.", exception);
        }
    }

    @Override
    public void unsubscribeAll(String socketId) {
        try {
            String subscriptionsKey = subscriptionsKey(socketId);
            Set<String> conversationIds = redisTemplate.opsForSet().members(subscriptionsKey);
            if (conversationIds != null) {
                for (String conversationId : conversationIds) {
                    String subscribersKey = subscribersKey(conversationId);
                    redisTemplate.opsForSet().remove(subscribersKey, socketId);
                    deleteIfEmpty(subscribersKey);
                }
            }
            redisTemplate.delete(subscriptionsKey);
        } catch (Exception exception) {
            log.warn("Failed to remove all typing subscriptions.", exception);
        }
    }

    @Override
    public Set<String> findSubscriberSocketIds(UUID conversationId) {
        try {
            Set<String> socketIds = redisTemplate.opsForSet().members(subscribersKey(conversationId));
            return socketIds == null ? Set.of() : socketIds;
        } catch (Exception exception) {
            log.warn("Failed to read typing subscriber socket ids.", exception);
            return Set.of();
        }
    }

    private void deleteIfEmpty(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        if (size != null && size == 0) {
            redisTemplate.delete(key);
        }
    }

    private String subscribersKey(UUID conversationId) {
        return subscribersKey(conversationId.toString());
    }

    private String subscribersKey(String conversationId) {
        return "typing_subscribers:" + conversationId;
    }

    private String subscriptionsKey(String socketId) {
        return "typing_subscriptions:" + socketId;
    }
}
