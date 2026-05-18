package com.synchat.backend.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Outbox outbox) {

    public AppProperties {
        if (outbox == null) {
            outbox = new Outbox(true, 50, 1000);
        }
    }

    public record Jwt(
            String accessSecret,
            String refreshSecret,
            long accessTtlMinutes,
            long refreshTtlDays
    ) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Outbox(
            boolean enabled,
            int batchSize,
            long fixedDelayMs
    ) {
    }
}
