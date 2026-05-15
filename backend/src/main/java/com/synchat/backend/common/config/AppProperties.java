package com.synchat.backend.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors) {

    public record Jwt(
            String accessSecret,
            String refreshSecret,
            long accessTtlMinutes,
            long refreshTtlDays
    ) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
