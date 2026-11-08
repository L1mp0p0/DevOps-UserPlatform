package org.example.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfiguration {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r.path("/api/users/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("userServiceCircuitBreaker")
                                .setFallbackUri("forward:/fallback/user-service")))
                        .uri("lb://user-service"))
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("notificationServiceCircuitBreaker")
                                .setFallbackUri("forward:/fallback/notification-service")))
                        .uri("lb://notification-service"))
                .build();
    }
}
