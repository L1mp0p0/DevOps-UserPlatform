package org.example.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест HTTP API: запрос -> контроллер -> JavaMailSender -> настоящий SMTP (GreenMail в памяти).
 * Проверяем, что письмо реально дошло до почтового сервера с нужным адресом, темой и текстом.
 */
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "user-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class NotificationApiIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail =
            new GreenMailExtension(new ServerSetup(32525, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearMailbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    private void postJson(String json, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void shouldSendCreatedMessage() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"operation\":\"CREATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.email").value("user@example.com"));

        assertTrue(greenMail.waitForIncomingEmail(5000, 1));
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);

        MimeMessage mail = messages[0];
        assertEquals("user@example.com", mail.getAllRecipients()[0].toString());
        assertEquals("noreply@test.local", mail.getFrom()[0].toString());
        assertEquals("Аккаунт создан", mail.getSubject());
        assertEquals("Здравствуйте! Ваш аккаунт на сайте ваш сайт был успешно создан.",
                ((String) mail.getContent()).trim());
    }

    @Test
    void shouldSendDeletedMessage() throws Exception {
        postJson("{\"email\":\"bye@example.com\",\"operation\":\"DELETED\"}", 200);

        assertTrue(greenMail.waitForIncomingEmail(5000, 1));
        MimeMessage mail = greenMail.getReceivedMessages()[0];

        assertEquals("bye@example.com", mail.getAllRecipients()[0].toString());
        assertEquals("Аккаунт удалён", mail.getSubject());
        assertEquals("Здравствуйте! Ваш аккаунт был удалён.", ((String) mail.getContent()).trim());
    }

    @Test
    void shouldReturn400AndSendNothingWhenEmailInvalid() throws Exception {
        postJson("{\"email\":\"not-an-email\",\"operation\":\"CREATED\"}", 400);

        assertEquals(0, greenMail.getReceivedMessages().length);
    }

    @Test
    void shouldReturn400WhenOperationMissing() throws Exception {
        postJson("{\"email\":\"user@example.com\"}", 400);

        assertEquals(0, greenMail.getReceivedMessages().length);
    }

    @Test
    void shouldReturn400WhenOperationUnknown() throws Exception {
        postJson("{\"email\":\"user@example.com\",\"operation\":\"EXPLODED\"}", 400);

        assertEquals(0, greenMail.getReceivedMessages().length);
    }
}
