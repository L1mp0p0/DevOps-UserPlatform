# user-service

CRUD пользователей: `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}`.
При создании и удалении (после коммита в БД) отправляет событие в Kafka-топик `user-events`.
Конфигурация (порт 8081, БД, Kafka) берётся из Config Server, регистрируется в Eureka.
Подробности запуска см. в корневом README.
