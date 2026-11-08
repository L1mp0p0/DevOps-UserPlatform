package org.example.kafka;

import org.example.event.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Отправляет событие в Kafka только ПОСЛЕ успешного коммита транзакции,
 * чтобы не уведомить пользователя о том, что в БД так и не произошло.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final String topic;

    public UserEventPublisher(KafkaTemplate<String, UserEvent> kafkaTemplate,
                              @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserEvent event) {
        kafkaTemplate.send(topic, event.email(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Не удалось отправить {} в Kafka (email={})",
                                event.operation(), event.email(), ex);
                    } else {
                        log.info("Событие {} отправлено в Kafka, topic={}, offset={}",
                                event.operation(), topic, result.getRecordMetadata().offset());
                    }
                });
    }
}
