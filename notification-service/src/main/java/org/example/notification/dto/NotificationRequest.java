package org.example.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(

        @NotBlank(message = "не должно быть пустым")
        @Email(message = "некорректный email")
        String email,

        @NotNull(message = "обязательное поле: CREATED или DELETED")
        UserOperation operation
) {
}
