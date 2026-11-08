package org.example.notification.dto;

/** Сообщение из Kafka: {"operation":"CREATED","email":"alex@mail.ru"} */
public record UserEventMessage(UserOperation operation, String email) {
}
