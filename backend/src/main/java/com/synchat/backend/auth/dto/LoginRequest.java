package com.synchat.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password,
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 50) String platform
) {
}
