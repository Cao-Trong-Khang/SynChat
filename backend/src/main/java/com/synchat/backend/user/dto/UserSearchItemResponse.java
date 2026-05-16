package com.synchat.backend.user.dto;

import java.util.UUID;

public record UserSearchItemResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
