package org.example.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.loadbalancer.cache.enabled=false",
        "gateway.circuit-breaker.timeout=500ms",
        "gateway.circuit-breaker.sliding-window-size=4",
        "gateway.circuit-breaker.minimum-calls=2",
        "gateway.circuit-breaker.failure-rate-threshold=50",
        "gateway.circuit-breaker.wait-in-open-state=2s"
})
class GatewayCircuitBreakerStateTest {

    private static final StubServer userService = new StubServer();

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.user-service[0].uri", userService::url);
    }

    @AfterAll
    static void stop() {
        userService.close();
    }

    @Autowired
    private WebTestClient client;

    private int statusOfGetUsers() {
        return client.mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .get().uri("/api/users")
                .exchange()
                .returnResult(String.class)
                .getStatus().value();
    }

    @Test
    void circuitShouldOpenShortCircuitAndThenRecover() {
        // 1. сервис "завис": два вызова подряд упираются в таймаут, цепь размыкается
        userService.setMode(StubServer.Mode.SLOW);
        assertEquals(503, statusOfGetUsers());
        assertEquals(503, statusOfGetUsers());
        int callsBeforeOpen = userService.requestCount();

        // 2. сервис "починился", но цепь OPEN: отвечает fallback, до сервиса запрос не доходит
        userService.setMode(StubServer.Mode.OK);
        assertEquals(503, statusOfGetUsers());
        assertEquals(callsBeforeOpen, userService.requestCount(),
                "пока цепь разомкнута, запросы не должны доходить до сервиса");

        // 3. через wait-in-open-state цепь пропускает пробные вызовы и закрывается
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertEquals(200, statusOfGetUsers()));
        assertEquals(200, statusOfGetUsers());
    }
}
