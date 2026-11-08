package org.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(

        @NotBlank(message = "не должно быть пустым")
        String name,

        @NotBlank(message = "не должно быть пустым")
        @Email(message = "некорректный email")
        String email,

        @NotNull(message = "обязательное поле")
        @Min(value = 0, message = "не может быть отрицательным")
        @Max(value = 150, message = "слишком большое значение")
        Integer age
) {
}
