# notification-service

Слушает Kafka-топик `user-events` и отправляет письма; плюс `POST /api/notifications`
с телом `{"email":"a@b.com","operation":"CREATED"|"DELETED"}` (200, 400, 502).
Конфигурация (порт 8082, Kafka, SMTP) берётся из Config Server, регистрируется в Eureka.
Логин и пароль почты: переменные окружения `SPRING_MAIL_USERNAME` и `SPRING_MAIL_PASSWORD`.
