package com.synchat.backend.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.notification.model.MessageCreatedNotificationData;
import com.synchat.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MessageCreatedNotificationParser {

    private static final int MESSAGE_PREVIEW_MAX_LENGTH = 120;

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public MessageCreatedNotificationParser(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    public Optional<MessageCreatedNotificationData> parse(UUID targetUserId, Object payload) {
        try {
            JsonNode root = objectMapper.valueToTree(payload);
            JsonNode conversation = required(root, "conversation");
            JsonNode message = required(root, "message");

            UUID conversationId = requiredUuid(conversation, "conversationId");
            UUID senderId = requiredUuid(message, "senderId");
            Long messageSeq = requiredLong(message, "seq");
            UUID clientMessageId = requiredUuid(message, "clientMessageId");
            String body = requiredText(message, "body");

            if (targetUserId != null && targetUserId.equals(senderId)) {
                return Optional.empty();
            }

            String senderDisplayName = userRepository.findById(senderId)
                    .map(user -> user.getDisplayName())
                    .filter(displayName -> !displayName.isBlank())
                    .orElse("New message");

            return Optional.of(new MessageCreatedNotificationData(
                    conversationId,
                    senderId,
                    targetUserId,
                    senderDisplayName,
                    preview(body),
                    messageSeq,
                    clientMessageId
            ));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Invalid message notification payload.");
        }
    }

    private JsonNode required(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing field: " + fieldName);
        }
        return value;
    }

    private UUID requiredUuid(JsonNode node, String fieldName) {
        String value = requiredText(node, fieldName);
        return UUID.fromString(value);
    }

    private Long requiredLong(JsonNode node, String fieldName) {
        JsonNode value = required(node, fieldName);
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Invalid long field: " + fieldName);
        }
        return value.asLong();
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = required(node, fieldName);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Invalid text field: " + fieldName);
        }
        return value.asText();
    }

    private String preview(String body) {
        String trimmed = body.trim();
        if (trimmed.length() <= MESSAGE_PREVIEW_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MESSAGE_PREVIEW_MAX_LENGTH);
    }
}
