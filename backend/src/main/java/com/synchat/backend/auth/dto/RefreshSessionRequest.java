package com.synchat.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefreshSessionRequest(
        @NotBlank String refreshToken,
        @NotNull UUID deviceId
) {
}
