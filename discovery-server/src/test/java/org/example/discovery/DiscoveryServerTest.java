package org.example.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    @Test
    void shouldServeEurekaRegistryApi() {
        ResponseEntity<String> response = rest.exchange(
                "/eureka/apps", HttpMethod.GET, new HttpEntity<>(json()), String.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("applications"));
    }

    @Test
    void shouldAcceptServiceRegistration() {
        String instance = """
                {"instance":{"instanceId":"test-1","hostName":"localhost","app":"TEST-SERVICE",
                "ipAddr":"127.0.0.1","status":"UP","port":{"$":8099,"@enabled":"true"},
                "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
                """;

        ResponseEntity<String> registered = rest.exchange(
                "/eureka/apps/TEST-SERVICE", HttpMethod.POST, new HttpEntity<>(instance, json()), String.class);
        assertEquals(204, registered.getStatusCode().value());

        ResponseEntity<String> apps = rest.exchange(
                "/eureka/apps/TEST-SERVICE", HttpMethod.GET, new HttpEntity<>(json()), String.class);
        assertEquals(200, apps.getStatusCode().value());
        assertTrue(apps.getBody().contains("TEST-SERVICE"));
    }

    @Test
    void shouldServeDashboard() {
        assertEquals(200, rest.getForEntity("/", String.class).getStatusCode().value());
    }
}
