package com.synchat.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserProfileResponse user
) {
}
