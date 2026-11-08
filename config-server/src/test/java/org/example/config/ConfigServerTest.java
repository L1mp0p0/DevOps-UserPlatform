package org.example.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяем, что Config Server реально отдаёт конфигурацию сервисов по HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerTest {

    @Autowired
    private TestRestTemplate rest;

    private String config(String app) {
        ResponseEntity<String> response = rest.getForEntity("/" + app + "/default", String.class);
        assertEquals(200, response.getStatusCode().value(), "для " + app);
        assertNotNull(response.getBody());
        return response.getBody();
    }

    @Test
    void shouldServeUserServiceConfig() {
        String body = config("user-service");

        assertTrue(body.contains("\"name\":\"user-service\""));
        assertTrue(body.contains("spring.datasource.url"));
        assertTrue(body.contains("jdbc:postgresql://localhost:5432/user_service"));
        assertTrue(body.contains("app.kafka.topic"));
    }

    @Test
    void shouldServeNotificationServiceConfig() {
        String body = config("notification-service");

        assertTrue(body.contains("spring.mail.host"));
        assertTrue(body.contains("spring.kafka.consumer.group-id"));
        assertTrue(body.contains("\"server.port\":8082"));
    }

    @Test
    void shouldServeGatewayCircuitBreakerSettings() {
        String body = config("api-gateway");

        assertTrue(body.contains("gateway.circuit-breaker.timeout"));
        assertTrue(body.contains("gateway.circuit-breaker.failure-rate-threshold"));
    }

    @Test
    void shouldIncludeSharedConfigForEveryService() {
        String body = config("user-service");

        assertTrue(body.contains("eureka.client.service-url.defaultZone"));
        assertTrue(body.contains("http://localhost:8761/eureka/"));
    }

    private String config(String app, String profile) {
        ResponseEntity<String> response = rest.getForEntity("/" + app + "/" + profile, String.class);
        assertEquals(200, response.getStatusCode().value(), "для " + app + "/" + profile);
        assertNotNull(response.getBody());
        return response.getBody();
    }

    @Test
    void dockerProfileShouldPointUserServiceToContainers() {
        String body = config("user-service", "docker");

        assertTrue(body.contains("jdbc:postgresql://postgres:5432/user_service"));
        assertTrue(body.contains("kafka:9092"));
    }

    @Test
    void dockerProfileShouldPointNotificationServiceToMailpit() {
        String body = config("notification-service", "docker");

        assertTrue(body.contains("\"spring.mail.host\":\"mailpit\""));
        assertTrue(body.contains("kafka:9092"));
    }

    @Test
    void dockerProfileShouldPointEverythingToEurekaContainer() {
        String body = config("api-gateway", "docker");

        assertTrue(body.contains("http://discovery-server:8761/eureka/"));
        assertTrue(body.contains("spring.cloud.loadbalancer.cache.ttl"));
    }

    @Test
    void defaultProfileMustNotLeakDockerAddresses() {
        String body = config("user-service", "default");

        assertTrue(body.contains("localhost:5432"));
        assertTrue(!body.contains("postgres:5432"));
    }
}
