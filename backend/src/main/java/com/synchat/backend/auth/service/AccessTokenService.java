package com.synchat.backend.auth.service;

import com.synchat.backend.common.config.AppProperties;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private final AppProperties appProperties;

    public AccessTokenService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public AccessTokenResult createAccessToken(UUID userId, UUID sessionId, UUID deviceId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(appProperties.jwt().accessTtlMinutes(), ChronoUnit.MINUTES);

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("sessionId", sessionId.toString())
                .claim("deviceId", deviceId.toString())
                .claim("tokenType", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(accessSecretKey(), Jwts.SIG.HS256)
                .compact();

        return new AccessTokenResult(token, expiresAt);
    }

    public AuthPrincipal parseAndValidate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthorized();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(accessSecretKey())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            if (!"access".equals(claims.get("tokenType", String.class))) {
                throw unauthorized();
            }

            UUID userId = UUID.fromString(requiredClaim(claims.getSubject()));
            UUID sessionId = UUID.fromString(requiredClaim(claims.get("sessionId", String.class)));
            UUID deviceId = UUID.fromString(requiredClaim(claims.get("deviceId", String.class)));

            return new AuthPrincipal(userId, sessionId, deviceId);
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private SecretKey accessSecretKey() {
        String accessSecret = appProperties.jwt().accessSecret();
        byte[] secretBytes = accessSecret == null ? new byte[0] : accessSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "JWT access secret is too short.");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    private String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw unauthorized();
        }
        return value;
    }

    private AppException unauthorized() {
        return new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized.");
    }

    public record AccessTokenResult(String token, Instant expiresAt) {
    }
}
