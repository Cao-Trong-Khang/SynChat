package com.synchat.backend.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessagePageResponse(
        UUID conversationId,
        List<MessageItemResponse> messages,
        Long oldestSeq,
        Long newestSeq,
        boolean hasMoreBefore
) {
}
