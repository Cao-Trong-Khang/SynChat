package com.synchat.backend.conversation.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationCursorCodec {

    private final ObjectMapper objectMapper;

    public ConversationCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ConversationCursor cursor) {
        if (cursor == null) {
            return null;
        }

        try {
            CursorPayload payload = new CursorPayload(cursor.lastMessageAt(), cursor.conversationId());
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    public ConversationCursor decode(String encodedCursor) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor.getBytes(StandardCharsets.UTF_8));
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);
            if (payload.lastMessageAt() == null || payload.conversationId() == null) {
                throw invalidCursor();
            }
            return new ConversationCursor(payload.lastMessageAt(), payload.conversationId());
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    private AppException invalidCursor() {
        return new AppException(ErrorCode.VALIDATION_ERROR, "Invalid conversation cursor.");
    }

    private record CursorPayload(Instant lastMessageAt, UUID conversationId) {
    }
}
