package com.synchat.backend.device.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.device.dto.DeviceRegisterCommandPayload;
import com.synchat.backend.device.dto.DeviceRegisteredPayload;
import com.synchat.backend.device.service.DeviceService;
import com.synchat.backend.realtime.websocket.EphemeralWebSocketEventSender;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandContext;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandHandler;
import com.synchat.backend.realtime.websocket.command.WebSocketCommandTypes;
import com.synchat.backend.realtime.websocket.session.UserSessionBinding;
import org.springframework.stereotype.Component;

@Component
public class DeviceRegisterCommandHandler implements WebSocketCommandHandler {

    private static final String DEVICE_REGISTERED = "device.registered";

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final EphemeralWebSocketEventSender ephemeralWebSocketEventSender;

    public DeviceRegisterCommandHandler(
            ObjectMapper objectMapper,
            DeviceService deviceService,
            EphemeralWebSocketEventSender ephemeralWebSocketEventSender
    ) {
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.ephemeralWebSocketEventSender = ephemeralWebSocketEventSender;
    }

    @Override
    public String commandType() {
        return WebSocketCommandTypes.DEVICES_REGISTER;
    }

    @Override
    public void handle(WebSocketCommandContext context, JsonNode payload) {
        DeviceRegisterCommandPayload commandPayload;
        try {
            commandPayload = objectMapper.treeToValue(payload, DeviceRegisterCommandPayload.class);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payload.");
        }

        UserSessionBinding binding = context.binding();
        deviceService.registerDevice(binding.userId(), binding.deviceId(), commandPayload);
        ephemeralWebSocketEventSender.sendToSocket(
                binding.socketId(),
                DEVICE_REGISTERED,
                context.requestId(),
                new DeviceRegisteredPayload(commandPayload.deviceId())
        );
    }
}
