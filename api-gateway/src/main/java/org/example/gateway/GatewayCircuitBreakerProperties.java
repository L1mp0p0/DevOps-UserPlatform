package org.example.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "gateway.circuit-breaker")
public record GatewayCircuitBreakerProperties(
        @DefaultValue("10") int slidingWindowSize,
        @DefaultValue("5") int minimumCalls,
        @DefaultValue("50") float failureRateThreshold,
        @DefaultValue("10s") Duration waitInOpenState,
        @DefaultValue("3s") Duration timeout
) {
}
