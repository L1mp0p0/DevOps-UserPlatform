package org.example.notification.mail;

import org.example.notification.dto.UserOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    // адрес отправителя: app.mail.from, иначе логин почты, иначе заглушка
    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:${spring.mail.username:noreply@example.com}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Отправляет письмо с шаблонным текстом для данной операции. */
    public void sendOperationMessage(UserOperation operation, String email) {
        send(email, operation.subject(), operation.text());
    }

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Письмо отправлено: to={}, subject={}", to, subject);
    }
}
