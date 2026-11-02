# AQA-001 — приложение отклоняет допустимые буквы G–Z в токене

**Статус:** воспроизводится на предоставленном `internal-0.0.1-SNAPSHOT.jar`.

**Серьёзность:** высокая. Пользователь с корректным по документации токеном не может войти и пользоваться сервисом.

## Требование

Токен — строка длиной 32 символа, состоящая только из `A–Z0–9`. Буквы от G до Z входят в разрешённый алфавит.

## Воспроизведение автоматическим тестом

Выполните команду из корня проекта.

**Linux / macOS:**

```bash
./mvnw "-Dtest=InputValidationTest#allDocumentedLettersAreAccepted" test
```

**Windows (PowerShell):**

```powershell
.\mvnw.cmd "-Dtest=InputValidationTest#allDocumentedLettersAreAccepted" test
```

## Пример запроса для воспроизведения бага

При запущенном приложении с ключом из задания и внешнем `/auth`, настроенном на HTTP 200:

```http
POST /endpoint
Content-Type: application/x-www-form-urlencoded
Accept: application/json
X-Api-Key: qazWSXedc

token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAG&action=LOGIN
```

Токен содержит 31 букву A и одну G: всего 32 символа.

## Ожидаемый результат

Приложение отправляет POST `/auth` с этим токеном и после успешной аутентификации возвращает `{"result":"OK"}`. Токен становится доступен для ACTION и LOGOUT.

## Фактический результат

- HTTP 400 и JSON с `result=ERROR`.
- В `message` указано ограничение `^[0-9A-F]{32}$`.
- Обращение к `/auth` не происходит; сессия не создаётся.

## Окружение

- ОС: Linux.
- Java: OpenJDK 17.0.20.1.
- Maven: 3.9.12.
- WireMock: 3.13.2.
- JUnit: 5.11.4.
- Allure JUnit 5: 2.29.1.
- Приложение: internal-0.0.1-SNAPSHOT.jar.

