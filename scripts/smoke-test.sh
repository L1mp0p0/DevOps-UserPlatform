#!/usr/bin/env bash
# Сквозная проверка системы, запущенной через docker compose up.
# Запуск из корня проекта:  bash scripts/smoke-test.sh            (полный, с проверкой Circuit Breaker)
#                           bash scripts/smoke-test.sh --no-failover
# Нужны только bash и curl (в Windows - Git Bash).
set -u

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
EUREKA="${EUREKA_URL:-http://localhost:8761}"
CONFIG="${CONFIG_URL:-http://localhost:8888}"
MAILPIT="${MAILPIT_URL:-http://localhost:8025}"
FAILOVER=1
[ "${1:-}" = "--no-failover" ] && FAILOVER=0

if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"; else COMPOSE="docker-compose"; fi

PASS=0; FAIL=0
step() { echo; echo "== $1"; }
ok()   { echo "  [OK]   $1"; PASS=$((PASS + 1)); }
bad()  { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
check() { # check "описание" команда...  (успех = код возврата 0)
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@"; }
wait_for() { # wait_for секунды команда... : повторяет команду раз в 2 с, пока не получится
  local timeout="$1"; shift
  local end=$((SECONDS + timeout))
  until "$@" >/dev/null 2>&1; do
    [ "$SECONDS" -ge "$end" ] && return 1
    sleep 2
  done
}
json_int() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }
mails_to() { # количество писем адресату (по API Mailpit)
  curl -s -G --max-time 10 "$MAILPIT/api/v1/search" --data-urlencode "query=to:$1" | grep -o '"ID":"' | wc -l | tr -d ' '
}
has_mail() { [ "$(mails_to "$1")" -ge "$2" ]; }
mail_subjects() {
  curl -s -G --max-time 10 "$MAILPIT/api/v1/search" --data-urlencode "query=to:$1"
}

users_ok()  { [ "$(code "$GATEWAY/api/users")" = "200" ]; }
notify_ok() { [ "$(code -X POST "$GATEWAY/api/notifications" -H 'Content-Type: application/json' \
                   -d "{\"email\":\"$1\",\"operation\":\"CREATED\"}")" = "200" ]; }
notify_503() { [ "$(code -X POST "$GATEWAY/api/notifications" -H 'Content-Type: application/json' \
                    -d '{"email":"x@example.com","operation":"CREATED"}')" = "503" ]; }

STAMP="$(date +%s)"
EMAIL="smoke.$STAMP@example.com"
EMAIL_API="smoke.api.$STAMP@example.com"

step "1. Шлюз и маршрутизация (ждём, пока сервисы зарегистрируются в Eureka)"
if wait_for 180 users_ok; then ok "GET $GATEWAY/api/users -> 200 (шлюз нашёл user-service через Eureka)"
else bad "шлюз не вернул 200 за 180 с (смотри: $COMPOSE logs api-gateway user-service)"; fi

step "2. Service Discovery: все сервисы в реестре Eureka"
APPS="$(curl -s -H 'Accept: application/json' --max-time 10 "$EUREKA/eureka/apps")"
for app in API-GATEWAY USER-SERVICE NOTIFICATION-SERVICE; do
  echo "$APPS" | grep -q "\"name\":\"$app\"" && ok "$app зарегистрирован" || bad "$app НЕ найден в Eureka"
done

step "3. External Configuration: Config Server отдаёт docker-профиль"
check "user-service/docker содержит jdbc:postgresql://postgres" \
  bash -c "curl -s --max-time 10 '$CONFIG/user-service/docker' | grep -q 'jdbc:postgresql://postgres:5432'"
check "notification-service/docker указывает на mailpit" \
  bash -c "curl -s --max-time 10 '$CONFIG/notification-service/docker' | grep -q mailpit"

step "4. Создание пользователя через шлюз -> Kafka -> письмо"
curl -s -X DELETE --max-time 10 "$MAILPIT/api/v1/messages" >/dev/null
RESP="$(curl -s -w '\n%{http_code}' --max-time 15 -X POST "$GATEWAY/api/users" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"age\":30}")"
STATUS="$(echo "$RESP" | tail -1)"; BODY="$(echo "$RESP" | sed '$d')"
[ "$STATUS" = "201" ] && ok "POST /api/users -> 201 (user-service записал в PostgreSQL)" || bad "POST /api/users вернул $STATUS: $BODY"
USER_ID="$(echo "$BODY" | json_int id)"
[ -n "$USER_ID" ] && ok "создан пользователь id=$USER_ID" || bad "в ответе нет id"

if wait_for 40 has_mail "$EMAIL" 1; then
  ok "письмо о создании пришло на $EMAIL (user-service -> Kafka -> notification-service -> SMTP)"
  mail_subjects "$EMAIL" | grep -q "Аккаунт создан" && ok "тема письма: «Аккаунт создан»" || bad "тема письма не «Аккаунт создан»"
else
  bad "письмо о создании не пришло за 40 с (смотри: $COMPOSE logs notification-service kafka)"
fi

step "5. Чтение, обновление и повторный email"
[ "$(code "$GATEWAY/api/users/$USER_ID")" = "200" ] && ok "GET /api/users/$USER_ID -> 200" || bad "GET по id не вернул 200"
[ "$(code -X POST "$GATEWAY/api/users" -H 'Content-Type: application/json' \
     -d "{\"name\":\"Dup\",\"email\":\"$EMAIL\",\"age\":20}")" = "409" ] \
  && ok "повторный email -> 409" || bad "повторный email не вернул 409"
[ "$(code -X POST "$GATEWAY/api/users" -H 'Content-Type: application/json' \
     -d '{"name":"","email":"bad","age":-1}')" = "400" ] \
  && ok "невалидные данные -> 400" || bad "невалидные данные не вернули 400"

step "6. Удаление пользователя через шлюз -> Kafka -> письмо"
[ "$(code -X DELETE "$GATEWAY/api/users/$USER_ID")" = "204" ] && ok "DELETE -> 204" || bad "DELETE не вернул 204"
[ "$(code "$GATEWAY/api/users/$USER_ID")" = "404" ] && ok "после удаления GET -> 404" || bad "после удаления GET не вернул 404"
if wait_for 40 has_mail "$EMAIL" 2; then
  ok "письмо об удалении пришло"
  mail_subjects "$EMAIL" | grep -q "Аккаунт удалён" && ok "тема письма: «Аккаунт удалён»" || bad "нет письма с темой «Аккаунт удалён»"
else
  bad "письмо об удалении не пришло за 40 с"
fi

step "7. API notification-service напрямую через шлюз"
if notify_ok "$EMAIL_API"; then ok "POST /api/notifications -> 200"; else bad "POST /api/notifications не вернул 200"; fi
wait_for 20 has_mail "$EMAIL_API" 1 && ok "письмо по API пришло на $EMAIL_API" || bad "письмо по API не пришло"

if [ "$FAILOVER" = "1" ]; then
  step "8. Circuit Breaker: останавливаем notification-service"
  $COMPOSE stop notification-service >/dev/null 2>&1 && ok "notification-service остановлен" || bad "не удалось остановить notification-service"
  if wait_for 40 notify_503; then ok "шлюз отвечает fallback 503, а не падает"; else bad "ожидали 503 от шлюза"; fi
  FB="$(curl -s --max-time 10 -X POST "$GATEWAY/api/notifications" -H 'Content-Type: application/json' \
        -d '{"email":"x@example.com","operation":"CREATED"}')"
  echo "$FB" | grep -q "Service Unavailable" && ok "тело fallback: $FB" || bad "тело fallback неожиданное: $FB"
  [ "$(code "$GATEWAY/api/users")" = "200" ] && ok "user-service продолжает работать (сбой изолирован)" || bad "user-service перестал отвечать"

  step "9. Восстановление: запускаем notification-service обратно"
  $COMPOSE start notification-service >/dev/null 2>&1
  if wait_for 180 notify_ok "$EMAIL_API"; then ok "после перезапуска шлюз снова пропускает запросы (цепь закрылась)"
  else bad "сервис не восстановился за 180 с"; fi
fi

echo
echo "=============================="
echo " Итог: успешно $PASS, ошибок $FAIL"
echo "=============================="
[ "$FAIL" -eq 0 ]
