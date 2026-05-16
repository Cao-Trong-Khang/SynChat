package com.synchat.backend.conversation.dto;

import java.util.UUID;

public record PeerResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
