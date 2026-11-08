package org.example.notification.kafka;

import org.example.notification.dto.UserEventMessage;
import org.example.notification.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final EmailService emailService;

    public UserEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "${app.kafka.topic}")
    public void onUserEvent(UserEventMessage message) {
        if (message == null || message.operation() == null
                || message.email() == null || message.email().isBlank()) {
            log.warn("Пропущено некорректное сообщение: {}", message);
            return;
        }
        log.info("Получено событие {} для {}", message.operation(), message.email());
        emailService.sendOperationMessage(message.operation(), message.email());
    }
}
