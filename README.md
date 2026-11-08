| Паттерн | Модуль Spring Cloud | Где |
|---|---|---|
| API Gateway | Spring Cloud Gateway | `api-gateway`: единая точка входа, маршруты `/api/users/**` и `/api/notifications/**` |
| Service Discovery | Netflix Eureka | `discovery-server` (сервер), клиенты: gateway, user-service, notification-service. Шлюз ходит по `lb://имя-сервиса`, адреса не прописаны |
| Circuit Breaker | Spring Cloud CircuitBreaker + Resilience4j | `api-gateway`: на каждом маршруте, при сбое или таймауте отдаётся fallback 503 |
| External Configuration | Spring Cloud Config | `config-server`: файлы в `config-server/src/main/resources/config-repo` |

## Запуск всей системы в Docker (рекомендуемый способ)

Нужен только **Docker Desktop** (или Docker Engine + Compose v2). Java, Maven, PostgreSQL и Kafka на хосте ставить не нужно.
Docker'у желательно дать от 6 ГБ памяти (в сумме запускается 5 Java-приложений, Kafka и PostgreSQL).

```
docker compose up --build        # первая сборка занимает несколько минут (Maven качает зависимости)
```

Поднимаются 8 контейнеров, порядок запуска обеспечивают `depends_on` + healthcheck:

| Контейнер | Что это | Порт на хосте |
|---|---|---|
| `postgres` | PostgreSQL 16, база `user_service` (данные в volume `pgdata`) | 5432 |
| `kafka` | Kafka 3.9 (KRaft, один узел) | 29092 (для приложений на хосте) |
| `mailpit` | ловушка писем: **почту в Docker-режиме реально никуда не отправляем**, все письма видны в веб-интерфейсе | **8025** |
| `config-server` | Spring Cloud Config; конфиги читает из папки `config-server/src/main/resources/config-repo` (она смонтирована в контейнер) | 8888 |
| `discovery-server` | Eureka | 8761 |
| `user-service` | CRUD пользователей, события в Kafka (наружу не опубликован) | - |
| `notification-service` | Kafka -> письма, `/api/notifications` (наружу не опубликован) | - |
| `api-gateway` | единственная точка входа для клиентов | **8080** |

Как это устроено в контейнерах:

- Сервисы запускаются с профилем `docker` (`SPRING_PROFILES_ACTIVE=docker`). Config Server отдаёт им дополнительные файлы
  `*-docker.yml` из `config-repo`, где вместо `localhost` стоят имена контейнеров (`postgres`, `kafka`, `mailpit`, `discovery-server`).
  Код и образы при этом одинаковы для любого окружения, различается только внешняя конфигурация.
- Импорт конфигурации в compose сделан не `optional:`: если Config Server недоступен, сервис не стартует «с пустыми настройками»,
  а перезапускается (`restart: on-failure`) и дожидается его.
- Изменил что-то в `config-repo`: `docker compose restart config-server <сервис>`, пересборка образов не нужна.
- Остановить: `docker compose down` (данные БД сохраняются), полный сброс: `docker compose down -v`.

### Проверка, что всё взаимодействует

Автоматически (из корня проекта, Linux / macOS / Windows Git Bash):

```
bash scripts/smoke-test.sh
```

Скрипт ждёт готовности системы (до 3 минут после старта) и проверяет:
1. запрос через шлюз доходит до `user-service` (маршрутизация по `lb://`, адреса из Eureka);
2. в Eureka зарегистрированы `API-GATEWAY`, `USER-SERVICE`, `NOTIFICATION-SERVICE`;
3. Config Server отдаёт docker-профиль;
4. `POST /api/users` -> 201, запись в PostgreSQL, событие в Kafka, письмо «Аккаунт создан» в Mailpit;
5. чтение, 409 на повторный email, 400 на невалидные данные;
6. `DELETE` -> 204, письмо «Аккаунт удалён»;
7. `POST /api/notifications` через шлюз, письмо пришло;
8. Circuit Breaker: останавливает `notification-service`, шлюз отвечает fallback 503, а `user-service` продолжает работать;
9. запускает `notification-service` обратно и ждёт, пока шлюз снова начнёт пропускать запросы.

Без остановки сервисов: `bash scripts/smoke-test.sh --no-failover`. Итог выводится в конце, код возврата ненулевой при ошибках.

Вручную (в PowerShell пиши `curl.exe`, а не `curl`):

```
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"age\":20}"
```

Затем открой http://localhost:8025: там появится письмо «Аккаунт создан». Реестр сервисов: http://localhost:8761,
конфигурация: http://localhost:8888/user-service/docker.

Если что-то не поднялось: `docker compose ps` (статусы health) и `docker compose logs -f <сервис>`.

## Что где настраивается

- Локальный `application.yml` каждого сервиса содержит только имя и `spring.config.import: optional:configserver:...`.
- Всё остальное лежит в `config-repo/`: `application.yml` (общее: адрес Eureka, actuator), `user-service.yml` (порт, БД, Kafka),
  `notification-service.yml` (порт, Kafka, SMTP), `api-gateway.yml` (порт и параметры Circuit Breaker).
- Секреты в репозиторий конфигурации не кладём: логин и пароль почты передаются переменными окружения
  `SPRING_MAIL_USERNAME` и `SPRING_MAIL_PASSWORD` при запуске `notification-service`. Пароль БД в `user-service.yml` учебный
  (`123`); для настоящего проекта его шифруют (`{cipher}...`) или берут из Vault.
- Config Server может читать конфигурацию из Git: инструкция в комментарии в `config-server/src/main/resources/application.yml`.
- Изменил файл в `config-repo`: перезапусти Config Server и затем нужный сервис.

## Порты

| Сервис | Порт |
|---|---|
| api-gateway (единственный адрес для клиентов) | 8080 |
| user-service | 8081 |
| notification-service | 8082 |
| config-server | 8888 |
| discovery-server (Eureka dashboard) | 8761 |