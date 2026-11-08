package org.example.notification.dto;

/** Тип операции + текст письма для неё. */
public enum UserOperation {

    CREATED("Аккаунт создан",
            "Здравствуйте! Ваш аккаунт на сайте ваш сайт был успешно создан."),

    DELETED("Аккаунт удалён",
            "Здравствуйте! Ваш аккаунт был удалён.");

    private final String subject;
    private final String text;

    UserOperation(String subject, String text) {
        this.subject = subject;
        this.text = text;
    }

    public String subject() {
        return subject;
    }

    public String text() {
        return text;
    }
}
