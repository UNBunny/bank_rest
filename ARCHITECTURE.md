# Архитектура: Система управления банковскими картами

## 1. Обзор

**Тип:** REST API backend (Spring Boot)  
**Язык:** Java 17+  
**Паттерн:** Layered Architecture (Controller → Service → Repository) с чёткими границами через DTO/маппинг  
**Принципы:** SOLID, Clean Code, Defense in Depth

---

## 2. Структура пакетов

```
com.example.bankcards
├── config/                        # Конфигурация Spring (Security, Swagger, Beans)
│   ├── SecurityConfig.java
│   ├── SwaggerConfig.java
│   └── ApplicationConfig.java
│
├── controller/                    # REST-контроллеры (входная точка HTTP)
│   ├── AuthController.java
│   ├── CardController.java
│   └── UserController.java
│
├── service/                       # Бизнес-логика (интерфейс + реализация)
│   ├── AuthService.java           # interface
│   ├── CardService.java           # interface
│   ├── UserService.java           # interface
│   └── impl/
│       ├── AuthServiceImpl.java
│       ├── CardServiceImpl.java
│       └── UserServiceImpl.java
│
├── repository/                    # Spring Data JPA репозитории
│   ├── CardRepository.java
│   └── UserRepository.java
│
├── entity/                        # JPA сущности (только персистентность)
│   ├── Card.java
│   ├── User.java
│   └── enums/
│       ├── CardStatus.java        # ACTIVE, BLOCKED, EXPIRED
│       └── Role.java              # ADMIN, USER
│
├── dto/                           # Data Transfer Objects
│   ├── request/
│   │   ├── AuthLoginRequest.java
│   │   ├── AuthRegisterRequest.java
│   │   ├── CardCreateRequest.java
│   │   ├── CardTransferRequest.java
│   │   └── CardBlockRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── CardResponse.java
│       ├── UserResponse.java
│       └── PageResponse.java      # обёртка для пагинации
│
├── mapper/                        # MapStruct маппинг Entity ↔ DTO
│   ├── CardMapper.java
│   └── UserMapper.java
│
├── security/                      # JWT + Spring Security
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
│
├── exception/                     # Кастомные исключения + глобальный обработчик
│   ├── GlobalExceptionHandler.java
│   ├── CardNotFoundException.java
│   ├── UserNotFoundException.java
│   ├── InsufficientFundsException.java
│   ├── CardBlockedException.java
│   └── AccessDeniedException.java
│
└── util/                          # Утилиты
    ├── CardNumberEncryptor.java   # шифрование AES
    └── CardNumberMasker.java      # маска **** **** **** 1234
```

---

## 3. Слои и их ответственность (SRP)

| Слой | Класс | Ответственность |
|------|-------|-----------------|
| **Controller** | `@RestController` | Приём HTTP, делегирование в Service, возврат HTTP ответа |
| **Service** | `interface` + `impl` | Вся бизнес-логика, транзакции, вызов Repository |
| **Repository** | `JpaRepository` | Только CRUD + кастомные запросы к БД |
| **Entity** | `@Entity` | Только описание таблицы — никакой логики |
| **DTO** | `record` / `class` | Только данные + аннотации валидации |
| **Mapper** | MapStruct | Только преобразование DTO ↔ Entity |
| **Security** | JWT Filter + Provider | Только аутентификация/авторизация |
| **Exception** | `@ControllerAdvice` | Только обработка ошибок → HTTP ответ |
| **Util** | утилиты | Шифрование, маскирование — без состояния |

---

## 4. Соответствие SOLID

### S — Single Responsibility
- Каждый класс отвечает за одно: контроллер не знает о БД, сервис не знает о HTTP, маппер только маппит.

### O — Open/Closed
- Сервисы объявлены через **интерфейсы** (`CardService`). Новое поведение — новая реализация, без изменения существующего кода.

### L — Liskov Substitution
- Все `ServiceImpl` заменяемы через их интерфейс. Тесты пишутся против интерфейсов — не реализаций.

### I — Interface Segregation
- Разделение: `AuthService`, `CardService`, `UserService` — не один "мегасервис".  
- Контроллеры зависят только от нужного интерфейса сервиса.

### D — Dependency Inversion
- Контроллеры зависят от **интерфейсов** сервисов (не реализаций).  
- Сервисы зависят от **интерфейсов** репозиториев (Spring Data).  
- Все зависимости через `@Autowired` / constructor injection.

> **Правило:** только **constructor injection** (не `@Autowired` на поле) — ради тестируемости и явности.

---

## 5. Entities

### User
```
id          UUID (PK)
email       VARCHAR UNIQUE NOT NULL
password    VARCHAR NOT NULL         ← bcrypt
role        ENUM(ADMIN, USER)
```

### Card
```
id              UUID (PK)
encryptedNumber VARCHAR NOT NULL     ← AES шифрование
maskedNumber    VARCHAR NOT NULL     ← **** **** **** 1234
owner           FK → User
expirationDate  DATE NOT NULL
status          ENUM(ACTIVE, BLOCKED, EXPIRED)
balance         DECIMAL(19,4) NOT NULL
```

---

## 6. DTO и Валидация

Все request-DTO аннотированы через **Jakarta Validation** (`@Valid` на контроллере).

### Пример: `CardCreateRequest`
```java
public record CardCreateRequest(
    @NotNull UUID ownerId,
    @NotNull @Future LocalDate expirationDate,
    @NotNull @DecimalMin("0.0") BigDecimal initialBalance
) {}
```

### Пример: `CardTransferRequest`
```java
public record CardTransferRequest(
    @NotNull UUID fromCardId,
    @NotNull UUID toCardId,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {}
```

### Пример: `AuthRegisterRequest`
```java
public record AuthRegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}
```

**GlobalExceptionHandler** перехватывает `MethodArgumentNotValidException` и возвращает структурированный JSON с полями ошибок.

---

## 7. Маппинг (MapStruct)

**Библиотека:** MapStruct 1.5.x — генерирует код маппинга на этапе компиляции, нулевой оверхед в рантайме.

```java
@Mapper(componentModel = "spring")
public interface CardMapper {
    CardResponse toResponse(Card card);
    Card toEntity(CardCreateRequest request);
    List<CardResponse> toResponseList(List<Card> cards);
}
```

**Правила маппинга:**
- `encryptedNumber` → не попадает в `CardResponse` (игнорируется)
- `maskedNumber` → попадает в `CardResponse`
- `balance` → `BigDecimal`, отображается как есть
- `status` → строка (`ACTIVE`, `BLOCKED`, `EXPIRED`)

---

## 8. Безопасность

### JWT Flow
```
POST /api/auth/login → JwtTokenProvider.generateToken() → Bearer token
Каждый запрос → JwtAuthenticationFilter → валидация → SecurityContextHolder
```

### Ролевой доступ

| Endpoint | ADMIN | USER |
|----------|-------|------|
| `POST /api/cards` | ✅ | ❌ |
| `DELETE /api/cards/{id}` | ✅ | ❌ |
| `PATCH /api/cards/{id}/block` | ✅ | ✅ (только своя) |
| `PATCH /api/cards/{id}/activate` | ✅ | ❌ |
| `GET /api/cards` | ✅ (все) | ✅ (только свои) |
| `POST /api/cards/transfer` | ❌ | ✅ (только свои) |
| `GET /api/users` | ✅ | ❌ |

### Шифрование карт
- Номер карты хранится в БД **зашифрованным** (AES-256)
- При отображении применяется маска: `**** **** **** 1234`
- Ключ шифрования — в `application.yml` (env variable, не в коде)

---

## 9. Обработка ошибок

Единая точка — `@ControllerAdvice GlobalExceptionHandler`.

| Исключение | HTTP статус |
|-----------|-------------|
| `CardNotFoundException` | 404 |
| `UserNotFoundException` | 404 |
| `InsufficientFundsException` | 422 |
| `CardBlockedException` | 409 |
| `AccessDeniedException` | 403 |
| `MethodArgumentNotValidException` | 400 |
| `Exception` (fallback) | 500 |

**Формат ответа ошибки:**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "timestamp": "2026-03-29T00:00:00",
  "details": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 255"
  }
}
```

---

## 10. API Endpoints

### Auth
```
POST /api/auth/register   — регистрация
POST /api/auth/login      — получение JWT
```

### Cards (USER)
```
GET    /api/cards                  — свои карты (пагинация + фильтр по статусу)
GET    /api/cards/{id}             — карта по ID (только своя)
GET    /api/cards/{id}/balance     — баланс карты
POST   /api/cards/transfer         — перевод между своими картами
PATCH  /api/cards/{id}/block       — запрос на блокировку
```

### Cards (ADMIN)
```
GET    /api/admin/cards            — все карты (пагинация + фильтр)
POST   /api/admin/cards            — создать карту для пользователя
DELETE /api/admin/cards/{id}       — удалить карту
PATCH  /api/admin/cards/{id}/block     — заблокировать
PATCH  /api/admin/cards/{id}/activate  — активировать
```

### Users (ADMIN)
```
GET    /api/admin/users            — все пользователи
GET    /api/admin/users/{id}       — пользователь по ID
DELETE /api/admin/users/{id}       — удалить пользователя
```

---

## 11. Миграции БД (Liquibase)

```
src/main/resources/db/migration/
├── changelog-master.xml
├── v1.0/
│   ├── 001_create_users_table.sql
│   ├── 002_create_cards_table.sql
│   └── 003_insert_admin_user.sql
└── v1.1/
    └── 004_add_card_indexes.sql
```

---

## 12. Тестирование

### Пирамида тестов

```
       ┌──────────┐
       │   E2E    │  ← Testcontainers + MockMvc (полный HTTP цикл с реальной БД)
       ├──────────┤
       │Integration│ ← @SpringBootTest / @DataJpaTest (слой + зависимости)
       ├──────────┤
       │  Unit    │ ← JUnit 5 + Mockito (изолированная логика)
       └──────────┘
```

### Unit тесты (src/test)
| Класс | Что тестируем |
|-------|---------------|
| `CardServiceImplTest` | Логика перевода, блокировки, валидация баланса |
| `UserServiceImplTest` | Регистрация, поиск, конфликты |
| `JwtTokenProviderTest` | Генерация/валидация токена |
| `CardNumberEncryptorTest` | Шифрование/дешифрование |
| `CardNumberMaskerTest` | Формат маски |
| `CardMapperTest` | Корректность маппинга полей |

### Integration тесты
| Класс | Что тестируем |
|-------|---------------|
| `CardRepositoryTest` | `@DataJpaTest` — кастомные запросы, пагинация |
| `UserRepositoryTest` | `@DataJpaTest` — findByEmail, уникальность |

### E2E тесты (Testcontainers + MockMvc)
| Класс | Что тестируем |
|-------|---------------|
| `AuthControllerE2ETest` | Регистрация, логин, получение токена |
| `CardControllerE2ETest` | CRUD карт, перевод, пагинация, фильтрация |
| `SecurityE2ETest` | Ролевой доступ — 401/403 для неавторизованных |

**Инструменты:**
- JUnit 5
- Mockito
- AssertJ
- Testcontainers (PostgreSQL)
- MockMvc
- Spring Security Test (`@WithMockUser`)

---

## 13. Зависимости (pom.xml)

```xml
<!-- Core -->
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation

<!-- DB -->
postgresql
liquibase-core

<!-- JWT -->
jjwt-api : 0.12.x
jjwt-impl : 0.12.x
jjwt-jackson : 0.12.x

<!-- Маппинг -->
mapstruct : 1.5.5.Final
lombok-mapstruct-binding

<!-- Документация -->
springdoc-openapi-starter-webmvc-ui : 2.x

<!-- Тесты -->
spring-boot-starter-test
spring-security-test
testcontainers (postgresql)
```

---

## 14. Docker Compose

```yaml
services:
  db:
    image: postgres:16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: bankdb
      POSTGRES_USER: bankuser
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    build: .
    ports: ["8080:8080"]
    depends_on: [db]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/bankdb
      JWT_SECRET: ${JWT_SECRET}
      CARD_ENCRYPTION_KEY: ${CARD_ENCRYPTION_KEY}
```

---

## 15. Выявленные проблемы текущей кодовой базы

| # | Проблема | Решение |
|---|---------|---------|
| 1 | `pom.xml` пустой (только комментарий) | Написать с нуля с правильными зависимостями |
| 2 | `application.yml` пустой | Настроить datasource, JWT, Liquibase, Swagger |
| 3 | `docker-compose.yml` не настроен | Написать конфиг с PostgreSQL + app |
| 4 | Нет ни одного Java класса | Реализовать с нуля по архитектуре выше |
| 5 | Нет Liquibase миграций | Создать changelog + скрипты |
| 6 | Нет тестов | Покрыть Unit + Integration + E2E |
| 7 | Риск: логика в Entity | Запрет — Entity только данные |
| 8 | Риск: прямой возврат Entity из Controller | Запрет — только DTO через маппер |
| 9 | Риск: номер карты в открытом виде | AES шифрование обязательно |
| 10 | Риск: секреты в коде | Только env variables |

---

## 16. Порядок разработки

```
1. pom.xml — зависимости
2. application.yml + docker-compose.yml
3. Entities + Enums
4. Liquibase миграции
5. Repositories
6. DTOs + Validation
7. Mappers (MapStruct)
8. Security (JWT)
9. Services (интерфейс → реализация)
10. Controllers
11. Exception Handling
12. Unit тесты
13. Integration тесты
14. E2E тесты
15. Swagger/OpenAPI документация
```
