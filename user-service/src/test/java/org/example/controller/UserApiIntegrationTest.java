package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.dto.UserRequest;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Сквозной тест: HTTP -> controller -> service -> JPA (H2) -> Kafka (встроенный).
 * Docker не нужен.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "user-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class UserApiIntegrationTest {

    private static final String TOPIC = "user-events";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private long createUser(String name, String email, int age) throws Exception {
        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRequest(name, email, age))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    /** Читает топик с начала и ждёт, пока наберётся нужное число событий для email. */
    private List<JsonNode> awaitEvents(String email, int expected) throws Exception {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<JsonNode> found = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 15_000;
            while (found.size() < expected && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode node = objectMapper.readTree(r.value());
                    if (email.equals(node.get("email").asText())) {
                        found.add(node);
                    }
                }
            }
        }
        return found;
    }

    @Test
    void fullCrudFlow() throws Exception {
        long id = createUser("Alex", "alex@mail.ru", 20);

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(put("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRequest("Alexander", "alex@mail.ru", 21))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alexander"))
                .andExpect(jsonPath("$.age").value(21));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSendKafkaEventOnCreate() throws Exception {
        createUser("Kate", "kate@mail.ru", 30);

        List<JsonNode> events = awaitEvents("kate@mail.ru", 1);

        assertEquals(1, events.size());
        assertEquals("CREATED", events.get(0).get("operation").asText());
    }

    @Test
    void shouldSendKafkaEventsOnCreateAndDelete() throws Exception {
        long id = createUser("Bob", "bob@mail.ru", 40);
        mockMvc.perform(delete("/api/users/" + id)).andExpect(status().isNoContent());

        List<JsonNode> events = awaitEvents("bob@mail.ru", 2);

        assertEquals(2, events.size());
        assertEquals("CREATED", events.get(0).get("operation").asText());
        assertEquals("DELETED", events.get(1).get("operation").asText());
    }

    @Test
    void shouldNotSendEventWhenEmailDuplicate() throws Exception {
        createUser("Alex", "dup@mail.ru", 20);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRequest("Other", "dup@mail.ru", 30))))
                .andExpect(status().isConflict());

        // ждём 2 события, но должно прийти только одно (от первого создания)
        assertEquals(1, awaitEvents("dup@mail.ru", 2).size());
    }

    @Test
    void shouldReturn409WhenUpdatingToExistingEmail() throws Exception {
        createUser("Alex", "alex@mail.ru", 20);
        long secondId = createUser("Ivan", "ivan@mail.ru", 25);

        mockMvc.perform(put("/api/users/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRequest("Ivan", "alex@mail.ru", 25))))
                .andExpect(status().isConflict());
    }
}
