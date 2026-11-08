package org.example.notification.dto;

public record NotificationResponse(String email, UserOperation operation, String subject, String status) {
}
