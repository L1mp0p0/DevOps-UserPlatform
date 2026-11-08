package org.example.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционный тест всей цепочки: сообщение в Kafka -> @KafkaListener -> EmailService -> SMTP (GreenMail).
 * Kafka встроенный (@EmbeddedKafka), SMTP в памяти (GreenMail), Docker не нужен.
 */
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "user-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaNotificationIntegrationTest {

    private static final String TOPIC = "user-events";

    @RegisterExtension
    static GreenMailExtension greenMail =
            new GreenMailExtension(new ServerSetup(32525, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @AfterEach
    void tearDown() {
        kafkaTemplate.destroy();
    }

    private void send(String json) throws Exception {
        kafkaTemplate.send(TOPIC, json).get(10, TimeUnit.SECONDS);
    }

    @Test
    void shouldSendEmailWhenUserCreated() throws Exception {
        send("{\"operation\":\"CREATED\",\"email\":\"new@user.com\"}");

        assertTrue(greenMail.waitForIncomingEmail(15_000, 1), "письмо не пришло");
        MimeMessage mail = greenMail.getReceivedMessages()[0];

        assertEquals("new@user.com", mail.getAllRecipients()[0].toString());
        assertEquals("Аккаунт создан", mail.getSubject());
        assertEquals("Здравствуйте! Ваш аккаунт на сайте ваш сайт был успешно создан.",
                ((String) mail.getContent()).trim());
    }

    @Test
    void shouldSendEmailWhenUserDeleted() throws Exception {
        send("{\"operation\":\"DELETED\",\"email\":\"old@user.com\"}");

        assertTrue(greenMail.waitForIncomingEmail(15_000, 1), "письмо не пришло");
        MimeMessage mail = greenMail.getReceivedMessages()[0];

        assertEquals("old@user.com", mail.getAllRecipients()[0].toString());
        assertEquals("Аккаунт удалён", mail.getSubject());
        assertEquals("Здравствуйте! Ваш аккаунт был удалён.", ((String) mail.getContent()).trim());
    }

    @Test
    void shouldSurviveBrokenMessageAndKeepProcessing() throws Exception {
        send("это не JSON");
        send("{\"operation\":\"CREATED\",\"email\":\"after-garbage@user.com\"}");

        assertTrue(greenMail.waitForIncomingEmail(15_000, 1), "письмо не пришло");
        MimeMessage[] messages = greenMail.getReceivedMessages();

        assertEquals(1, messages.length);   // мусор не породил письма
        assertEquals("after-garbage@user.com", messages[0].getAllRecipients()[0].toString());
    }

    @Test
    void shouldIgnoreMessageWithoutEmail() throws Exception {
        send("{\"operation\":\"CREATED\"}");
        send("{\"operation\":\"DELETED\",\"email\":\"valid@user.com\"}");

        assertTrue(greenMail.waitForIncomingEmail(15_000, 1), "письмо не пришло");
        MimeMessage[] messages = greenMail.getReceivedMessages();

        assertEquals(1, messages.length);
        assertEquals("valid@user.com", messages[0].getAllRecipients()[0].toString());
    }
}
