package org.example.event;

/**
 * Событие об операции над пользователем.
 * В Kafka уходит в JSON: {"operation":"CREATED","email":"alex@mail.ru"}
 */
public record UserEvent(UserOperation operation, String email) {
}
