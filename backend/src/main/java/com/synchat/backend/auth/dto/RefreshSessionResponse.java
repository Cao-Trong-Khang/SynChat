package com.synchat.backend.auth.dto;

import java.time.Instant;

public record RefreshSessionResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
