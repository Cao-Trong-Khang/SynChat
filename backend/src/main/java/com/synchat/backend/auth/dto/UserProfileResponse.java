package com.synchat.backend.auth.dto;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
