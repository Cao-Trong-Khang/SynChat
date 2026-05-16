package com.synchat.backend.device.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeviceStateCommandHandler implements WebSocketCommandHandler {

    private static final String FOREGROUND = "foreground";
    private static final String BACKGROUND = "background";

    private final StringRedisTemplate redisTemplate;

    public DeviceStateCommandHandler(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.DEVICE_STATE;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        if (payload == null || !payload.hasNonNull("state")) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Device state is required.");
        }

        String state = payload.get("state").asText();
        if (!FOREGROUND.equals(state) && !BACKGROUND.equals(state)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid device state.");
        }

        redisTemplate.opsForValue().set("device_state:" + context.binding().deviceId(), state);
    }
}
