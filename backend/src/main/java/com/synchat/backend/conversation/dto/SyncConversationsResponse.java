package com.synchat.backend.conversation.dto;

import java.util.List;

public record SyncConversationsResponse(
        List<ConversationMetadataResponse> items,
        String nextCursor,
        boolean hasMore,
        Long serverEventSeq
) {
}
