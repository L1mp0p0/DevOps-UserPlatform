package org.example.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.loadbalancer.cache.enabled=false",
        "gateway.circuit-breaker.timeout=1s"
})
class GatewayRoutingTest {

    private static final StubServer userService = new StubServer();
    private static final String deadNotificationService = StubServer.deadUrl();

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.user-service[0].uri", userService::url);
        registry.add("spring.cloud.discovery.client.simple.instances.notification-service[0].uri",
                () -> deadNotificationService);
    }

    @AfterAll
    static void stop() {
        userService.close();
    }

    @Autowired
    private WebTestClient client;

    @Test
    void shouldRouteGetToUserService() {
        client.get().uri("/api/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].name").isEqualTo("Alex");

        assertEquals("/api/users", userService.requests().getLast().path());
    }

    @Test
    void shouldForwardPostBodyAndStatus() {
        client.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Ivan\",\"email\":\"ivan@mail.ru\",\"age\":30}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo(2);

        StubServer.RecordedRequest received = userService.requests().getLast();
        assertEquals("POST", received.method());
        assertEquals("{\"name\":\"Ivan\",\"email\":\"ivan@mail.ru\",\"age\":30}", received.body());
    }

    @Test
    void shouldPassDownstreamErrorStatusThrough() {
        // 404 от самого сервиса - это не авария, Circuit Breaker его не подменяет
        client.get().uri("/api/users/99")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("not found");
    }

    @Test
    void shouldReturnFallbackWhenServiceIsDown() {
        client.post().uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.com\",\"operation\":\"CREATED\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("notification-service");
    }

    @Test
    void shouldReturnFallbackWhenServiceIsTooSlow() {
        // заглушка отвечает через 5 с, а таймаут Circuit Breaker - 1 с
        client.get().uri("/api/users/slow")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.service").isEqualTo("user-service");
    }

    @Test
    void shouldReturn404ForUnknownRoute() {
        client.get().uri("/api/unknown")
                .exchange()
                .expectStatus().isNotFound();
    }
}
