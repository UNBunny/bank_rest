# Bank Cards REST API

Backend-приложение на Spring Boot для управления банковскими картами с JWT-аутентификацией, шифрованием номеров карт и ролевым доступом.

## Технологии

- **Java 17**, Spring Boot 3.2.4, Spring Security
- **PostgreSQL 16** + Liquibase (миграции)
- **JWT** (аутентификация), **AES/GCM** (шифрование номеров карт)
- **Docker** + Docker Compose
- **Swagger UI** (OpenAPI 3)
- **JUnit 5**, Mockito, Testcontainers (тесты)

## Быстрый старт (Docker Compose)

### 1. Склонировать репозиторий

```bash
git clone https://github.com/UNBunny/bank_rest.git
cd bank_rest
```

### 2. Создать файл `.env`

```bash
cp .env.example .env
```

Заполнить `.env` своими значениями:

```env
# PostgreSQL
POSTGRES_DB=bankdb
POSTGRES_USER=bankuser
POSTGRES_PASSWORD=your_strong_password

# JWT secret — минимум 64 hex-символа (256 бит)
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# AES/GCM ключ шифрования карт — ровно 32 символа (256 бит)
CARD_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef

# Начальный admin-пользователь (создаётся Liquibase при первом запуске)
DB_ADMIN_EMAIL=admin@bank.com
# BCrypt-хэш пароля (пример: password = "admin123")
DB_ADMIN_PASSWORD_HASH=$2a$12$iAUGvQCz51BgZWlZ/8klIOf7w7UtVJi8CrTMSY2Krhd.QB4.r51NO

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

> **Как сгенерировать BCrypt-хэш:**
> ```bash
> # Через htpasswd (Apache utils)
> htpasswd -bnBC 12 "" yourpassword | tr -d ':\n'
> # Или онлайн: https://bcrypt-generator.com (cost factor 12)
> ```

### 3. Запустить

```bash
docker compose up --build
```

Приложение будет доступно на `http://localhost:8080`.

### 4. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Запуск без Docker (локально)

Требуется: **Java 17**, **Maven 3.8+**, запущенный **PostgreSQL**.

### 1. Создать базу данных

```sql
CREATE DATABASE bankdb;
CREATE USER bankuser WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE bankdb TO bankuser;
```

### 2. Установить переменные окружения

```bash
# Windows PowerShell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/bankdb"
$env:SPRING_DATASOURCE_USERNAME="bankuser"
$env:SPRING_DATASOURCE_PASSWORD="your_password"
$env:JWT_SECRET="404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
$env:CARD_ENCRYPTION_KEY="0123456789abcdef0123456789abcdef"
$env:DB_ADMIN_EMAIL="admin@bank.com"
$env:DB_ADMIN_PASSWORD_HASH='$2a$12$iAUGvQCz51BgZWlZ/8klIOf7w7UtVJi8CrTMSY2Krhd.QB4.r51NO'
$env:CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:8080"
```

### 3. Запустить приложение

```bash
mvn spring-boot:run
```

---

## Запуск тестов

Тесты используют **Testcontainers** — для E2E и интеграционных тестов нужен запущенный Docker.

```bash
# Все тесты
mvn test

# Только юнит-тесты (без Testcontainers)
mvn test -Dgroups="unit"

# Только конкретный класс
mvn test -Dtest=CardServiceImplTest
```

---

## API

| Метод | Путь | Роль | Описание |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Регистрация пользователя |
| `POST` | `/api/auth/login` | — | Получить JWT токен |
| `GET` | `/api/cards` | USER | Свои карты (фильтр, пагинация) |
| `GET` | `/api/cards/{id}` | USER | Карта по ID |
| `GET` | `/api/cards/{id}/balance` | USER | Баланс карты |
| `PATCH` | `/api/cards/{id}/block` | USER | Запросить блокировку |
| `POST` | `/api/cards/transfer` | USER | Перевод между своими картами |
| `GET` | `/api/admin/cards` | ADMIN | Все карты (фильтр, пагинация) |
| `POST` | `/api/admin/cards` | ADMIN | Создать карту |
| `DELETE` | `/api/admin/cards/{id}` | ADMIN | Удалить карту |
| `PATCH` | `/api/admin/cards/{id}/block` | ADMIN | Заблокировать карту |
| `PATCH` | `/api/admin/cards/{id}/activate` | ADMIN | Активировать карту |
| `GET` | `/api/admin/users` | ADMIN | Все пользователи |
| `DELETE` | `/api/admin/users/{id}` | ADMIN | Удалить пользователя |

Авторизация: заголовок `Authorization: Bearer <token>`.

---

## Структура проекта

```
src/main/java/com/example/bankcards/
├── config/          # SecurityConfig, SwaggerConfig, WebMvcConfig
├── controller/      # AuthController, CardController, AdminCardController, AdminUserController
├── dto/             # Request/Response DTOs
├── entity/          # User, Card, enums (Role, CardStatus)
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── mapper/          # MapStruct mappers
├── repository/      # JPA repositories
├── security/        # JWT provider, filter, CustomUserDetails
├── service/         # Business logic interfaces + implementations
└── util/            # CardNumberEncryptor (AES/GCM), CardNumberMasker
```

---

## Безопасность

- Номера карт шифруются **AES/GCM/NoPadding** (256-bit key, случайный IV) перед сохранением в БД
- Пользователям отображается только маска: `**** **** **** 1234`
- Переводы защищены **пессимистичной блокировкой** (`SELECT FOR UPDATE`) — исключает race condition
- JWT токены содержат `userId` и `role`; срок действия — 24 часа
