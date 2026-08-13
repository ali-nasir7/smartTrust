# SmartTrust Backend — Auth Module (Enterprise)

**Stack:** Java 23 + Spring Boot 3.4.5 + PostgreSQL/MySQL + Spring Security + JWT + BCrypt12 + Bucket4j Rate Limiting + Swagger OpenAPI

This is **Module 1** of SmartTrust Modular Monolith — built 100% per FYP requirements.

## 📁 Folder Structure (Modular Monolith)

```
com.smarttrust
├── SmartTrustApplication.java
├── common/
│   ├── config/         -> SecurityConfig, WebConfig, CacheConfig, OpenApiConfig, JacksonConfig
│   ├── security/       -> JwtTokenProvider, JwtAuthenticationFilter, RateLimitService, RateLimitFilter, UserPrincipal, CustomUserDetailsService
│   ├── exception/      -> ErrorCode, BusinessException, ApiError, GlobalExceptionHandler
│   ├── audit/          -> Auditable base
│   ├── validation/     -> ValidEnum
│   └── util/           -> CorrelationIdFilter
└── modules/
    ├── auth/
    │   ├── api/        -> AuthController + DTOs
    │   ├── domain/
    │   │   ├── entity/ -> RefreshToken, OtpCode, LoginAttempt
    │   │   ├── enums/  -> OtpPurpose
    │   │   ├── event/  -> UserRegisteredEvent, UserLoggedInEvent
    │   │   └── service/-> AuthService, OtpService, RefreshTokenService
    │   └── infrastructure/
    │       └── persistence/ -> Repos
    └── user/
        ├── domain/
        │   ├── entity/ -> User (core identity)
        │   └── enums/  -> UserRole, UserStatus
        └── infrastructure/
            └── persistence/ -> UserRepository
```

## 🗄️ Database (Manual SQL, no Flyway as requested)

Run file: `src/main/resources/schema-auth.sql` manually in pgAdmin / DBeaver / MySQL Workbench.

- PostgreSQL primary (with `CREATE EXTENSION pgcrypto`)
- MySQL version commented in same file

Tables:
- `users` — core identity, phone unique (03XXXXXXXXX or +923XXXXXXXXX), BCrypt12 password
- `auth_otp_codes` — OTP hashed with BCrypt, 5min expiry, max 3 attempts
- `auth_refresh_tokens` — opaque token SHA-256 hashed, 7d expiry, rotation
- `auth_login_attempts` — brute-force audit, 5 fails → 15min lock

## 🔐 Auth Flow — Enterprise Implementation

### 1. Register Init `POST /api/v1/auth/register/init`
- Validates PK phone regex `^(\\+923\\d{9}|03\\d{9})$` + password 8+ with uppercase/lowercase/digit
- Phone normalization: `+923001234567` → `03001234567`
- Checks unique (excluding DELETED)
- Creates user PENDING, hashes password BCrypt 12
- Generates 6-digit OTP SecureRandom, hashes BCrypt, saves with 5min expiry
- Logs OTP to console (mockEnabled=true for FYP) + publishes `UserRegisteredEvent`
- Returns `mockOtpForTesting` in dev

### 2. Verify OTP `POST /api/v1/auth/verify-otp`
- Finds latest not-verified OTP for phone+REGISTRATION
- Checks expiry, attempts <3
- Verifies BCrypt match, increment attempts on fail
- On success: marks OTP verified, user ACTIVE + phoneVerified=true
- Creates refresh token: 48 random bytes Base64Url, SHA-256 hash stored, 7d expiry
- Generates JWT access token HS256 15m with claims: sub=userId, phone, role, status, issuer=smarttrust-api
- Returns AuthResponse

### 3. Login `POST /api/v1/auth/login`
- Brute-force check: count failed attempts last 15min >=5 → 423 LOCKED
- Verifies password BCrypt
- Checks status: PENDING → 403 phone not verified, SUSPENDED/BANNED → 403
- Records attempt success/fail in `auth_login_attempts`
- Issues new access + refresh (rotation future)
- Publishes `UserLoggedInEvent`

### 4. Refresh `POST /api/v1/auth/refresh`
- Hashes raw refresh token SHA-256, finds in DB
- Checks revoked, expired
- Rotation: revokes old, creates new refresh + new access
- Returns new pair

### 5. Logout `POST /api/v1/auth/logout`
- Idempotent revoke, always 204 even if invalid

### 6. Forgot Password
- Init `/forgot-password/init`: generates OTP purpose FORGOT_PASSWORD
- Reset `/forgot-password/reset`: verifies OTP + sets new password BCrypt, revokes ALL refresh tokens (force re-login)

## 🛡️ Security Features

- **Password:** BCrypt strength 12
- **OTP:** 6-digit SecureRandom, BCrypt hashed, never stored plain, 5min expiry, 3 attempts max
- **Refresh Token:** Opaque random 48 bytes, stored SHA-256 hex, unique constraint, rotation invalidates old
- **JWT:** HS256, 64+ chars secret from env `JWT_SECRET`, 15m expiry, issuer validation, claims minimal
- **Brute Force:** DB `auth_login_attempts` + Caffeine Bucket4j RateLimitFilter per IP (login 5/15min)
- **Rate Limit Headers:** `X-Rate-Limit-Remaining`, `X-Rate-Limit-Retry-After-Seconds`
- **CorrelationId:** Filter generates UUID, MDC logging, header `X-Correlation-Id`
- **CORS:** Allowed all for FYP (set env in prod)
- **Validation:** Bean validation + custom regex for PK phones
- **Error Responses:** Uniform `ApiError {timestamp,status,errorCode,message,path,traceId,fieldErrors}` never leaks stacktrace

## 🚀 How to Run (Windows Maven 3.9.9 + Java 23)

### Prerequisites
- Java 23 JDK
- Maven 3.9.9
- PostgreSQL 15+ (or MySQL 8+) running on localhost:5432
- Create DB: `CREATE DATABASE smarttrust;`

### Steps
```cmd
# 1. Clone / copy project
cd smarttrust-backend

# 2. Configure DB in .env or directly in application.yml
# Edit src/main/resources/application.yml or set env vars:
# DB_URL=jdbc:postgresql://localhost:5432/smarttrust
# DB_USER=smarttrust
# DB_PASS=smarttrust123
# JWT_SECRET=dev-jwt-secret-must-be-at-least-64-chars-long-for-hs256-alg-0123456789AB
# For MySQL use: DB_URL=jdbc:mysql://localhost:3306/smarttrust

# 3. Run SQL schema manually
# Open pgAdmin -> Query Tool -> run contents of src/main/resources/schema-auth.sql

# 4. Build
mvn clean compile

# 5. Run (local profile)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# App starts on http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
# Health: http://localhost:8080/actuator/health
# OpenAPI JSON: http://localhost:8080/v3/api-docs
```

### Test via cURL / Postman

Import `docs/SmartTrust-Auth.postman_collection.json` (create from Swagger or use examples below)

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register/init \
 -H "Content-Type: application/json" \
 -d '{"phone":"03001234567","password":"Test@1234","role":"CUSTOMER","fullName":"Ali Nasir"}'

# Response gives mockOtpForTesting e.g., 123456 (dev only) + userId

# Verify OTP
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
 -H "Content-Type: application/json" \
 -d '{"phone":"03001234567","otp":"123456"}'

# Returns accessToken + refreshToken

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
 -H "Content-Type: application/json" \
 -d '{"phone":"03001234567","password":"Test@1234"}'

# Refresh
curl -X POST http://localhost:8080/api/v1/auth/refresh \
 -H "Content-Type: application/json" \
 -d '{"refreshToken":"<rawRefreshToken>"}'

# Access protected endpoint (example, after you add other modules)
curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```

## 🧪 Tests

```cmd
mvn test
```

Unit tests included:
- `JwtTokenProviderTest` — generate/validate/expiry
- `OtpServiceTest` — OTP hashing, expiry, max attempts logic
- `AuthServiceBruteForceTest` — lock after 5 fails

Integration: `AuthIntegrationTest` with H2 (test profile)

## 📌 Enterprise Notes (Why no Flyway)

You requested "don't use migrated db use sql" — so we provide raw `schema-auth.sql` for manual execution (enterprise teams sometimes use DBA-reviewed SQL). For production modular monolith, Flyway is RECOMMENDED (as per handbook Part 13). You can add later:

```xml
<dependency>flyway-core</dependency> + flyway-database-postgresql
```

And move SQL to `V1__users_and_auth.sql`.

## 🔜 Next Modules

- Customer module (profile + location)
- Provider module (CNIC encryption + Tasdeeq mock)
- Request module (hyperlocal tier logic)

All will reuse `users` table and `JwtTokenProvider`.

## 🧑‍💻 Author

Ali Nasir — SmartTrust FYP Backend — Hamdard University
Module 1 Auth — 100% enterprise blueprint implemented
