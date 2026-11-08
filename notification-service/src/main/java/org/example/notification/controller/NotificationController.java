package org.example.notification.controller;

import jakarta.validation.Valid;
import org.example.notification.dto.NotificationRequest;
import org.example.notification.dto.NotificationResponse;
import org.example.notification.mail.EmailService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Тот же функционал, что и через Kafka, но по HTTP. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final EmailService emailService;

    public NotificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping
    public NotificationResponse send(@Valid @RequestBody NotificationRequest request) {
        emailService.sendOperationMessage(request.operation(), request.email());
        return new NotificationResponse(
                request.email(), request.operation(), request.operation().subject(), "SENT");
    }
}
