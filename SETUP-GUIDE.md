# SmartTrust v2 — Email OTP + Provider Verification Setup Guide

New in v2 (on top of the existing auth module):

1. **Registration collects phone + email + password** → OTP is sent **by email** (branded "SmartTrust — Verify Your Email").
2. OTP rules: random (SecureRandom), 5 min expiry, single-use, 60 s resend cooldown, max 3 wrong attempts, old OTP invalidated on resend, BCrypt-hashed at rest, **never in API responses, never in production logs**.
3. After verifying the OTP the user is logged in and **selects a role: Customer or Service Provider**.
4. **Customer** → short form (name, address, city).
5. **Service Provider** → detailed form (category, experience, skills, bio…) → uploads **CNIC front + CNIC back + selfie** → status `PENDING_REVIEW`.
6. **Admin** reviews documents and **Approves / Rejects** (with reason; rejection allows resubmission).

Stack: Java 23.0.1 · Maven 3.9.9 · Spring Boot 3.4.x · MySQL 8 · SMTP (Gmail).

---

## 1. Database setup (MySQL)

**Fresh install** — in MySQL Workbench run the whole file:

```
src/main/resources/schema-auth.sql
```

**Upgrading an existing v1 database** (you already ran the old schema-auth.sql):

```
src/main/resources/db/upgrade-v2.sql
```

Both create/alter:
- `users` — role now NULLABLE (chosen after email verification), new `full_name` column
- `auth_otp_codes` — new `email`, `superseded_at` columns (+ email index)
- `service_categories` — seeded with 15 categories (Electrician, Plumber, AC Technician, …)
- `customer_profiles`, `service_provider_profiles`, `provider_documents` — new

## 2. Email (Gmail SMTP) — external service you must configure

Gmail requires a **16-character App Password** (ordinary account passwords are rejected):

1. Google account → **Security** → enable **2-Step Verification** (required for App Passwords).
2. Google account → Security → **App passwords** → create one (name it e.g. "SmartTrust").
3. Put those 16 characters (no spaces) into `SMTP_PASSWORD` below.

No 2FA available, or you just want to test without email first? Set `OTP_MOCK_ENABLED=true` —
the OTP prints on the server console (DEV ONLY; the API never returns it).

## 3. `.env` file (credentials never committed to Git)

Copy `.env.example` → `.env` in the project root (next to `pom.xml`) and edit:

```env
DB_URL=jdbc:mysql://localhost:3306/smarttrust?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASS=your_mysql_root_password

JWT_SECRET=paste-a-random-string-of-at-least-64-characters-here

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=yourgmail@gmail.com
SMTP_PASSWORD=your16charapppassword
MAIL_FROM=yourgmail@gmail.com
MAIL_FROM_NAME=SmartTrust

ADMIN_PHONE=03001234567
ADMIN_EMAIL=admin@smarttrust.pk
ADMIN_PASSWORD=Admin@123

UPLOAD_DIR=./uploads
OTP_MOCK_ENABLED=false
```

`ADMIN_PHONE`/`ADMIN_PASSWORD` auto-create the first ADMIN on startup (no-op if the phone exists or they're unset). Generate a JWT secret with any password manager, or paste any 64+ random characters.

## 4. Exact CMD commands (Windows, Java 23.0.1 + Maven 3.9.9)

```cmd
cd path\to\smartTrust

REM (once per machine) — checks
java -version      REM must print 23.0.1
mvn -version       REM must print Maven 3.9.9

REM 1. Run the SQL schema (step 1) in MySQL Workbench first!

REM 2. Create .env (step 3) — Notepad is fine
notepad .env

REM 3. Build
mvn clean compile

REM 4. Run (local profile)
mvn spring-boot:run -Dspring-boot.run.profiles=local

REM App:      http://localhost:8080
REM Swagger:  http://localhost:8080/swagger-ui.html
REM Health:   http://localhost:8080/actuator/health
```

If you prefer not to use `.env`, set the same variables in the same CMD window before `mvn spring-boot:run`:

```cmd
set DB_PASS=your_mysql_root_password
set SMTP_USERNAME=yourgmail@gmail.com
set SMTP_PASSWORD=your16charapppassword
set MAIL_FROM=yourgmail@gmail.com
set ADMIN_PHONE=03001234567
set ADMIN_PASSWORD=Admin@123
set JWT_SECRET=dev-jwt-secret-must-be-at-least-64-chars-long-for-hs256-alg-0123456789AB
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 5. Test credentials / data

| Purpose | Phone | Email | Password |
|---|---|---|---|
| Admin (auto-created from .env) | `03001234567` | `admin@smarttrust.pk` | `Admin@123` |
| New customer (register in the flow) | `03011111111` | your real inbox | `Test@1234` |
| New provider (register in the flow) | `03022222222` | your real inbox | `Test@1234` |

Any PK phone (`03XXXXXXXXX`) works. Use **your own Gmail inbox** so you receive the OTP emails.
For uploads use any JPG/PNG under 5 MB (phone photos of a CNIC front/back and a selfie).

## 6. Full end-to-end API walkthrough (curl)

```bat
:: 1) Register — OTP goes to EMAIL (response NEVER contains the OTP)
curl -X POST http://localhost:8080/api/v1/auth/register/init -H "Content-Type: application/json" -d "{\"phone\":\"03011111111\",\"email\":\"you@gmail.com\",\"password\":\"Test@1234\",\"fullName\":\"Ali Raza\"}"

:: 2) (optional) resend — blocked for 60 s after the previous code
curl -X POST http://localhost:8080/api/v1/auth/register/resend-otp -H "Content-Type: application/json" -d "{\"phone\":\"03011111111\"}"

:: 3) Verify the 6-digit code from your inbox -> tokens (role is null at this point)
curl -X POST http://localhost:8080/api/v1/auth/verify-otp -H "Content-Type: application/json" -d "{\"phone\":\"03011111111\",\"otp\":\"123456\"}"

:: 4) Select role
curl -X POST http://localhost:8080/api/v1/auth/select-role -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d "{\"role\":\"SERVICE_PROVIDER\"}"

:: 5a) CUSTOMER short form
curl -X POST http://localhost:8080/api/v1/customers/profile -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d "{\"fullName\":\"Ali Raza\",\"address\":\"House 12, Street 4\",\"city\":\"Rawalpindi\"}"

:: 5b) PROVIDER detailed form
curl -X POST http://localhost:8080/api/v1/providers/profile -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d "{\"fullName\":\"Kamran Iqbal\",\"categoryId\":1,\"experienceYears\":5,\"skills\":[\"Wiring\",\"Fan install\"],\"bio\":\"Certified electrician\",\"address\":\"Plaza 9, Saddar\",\"city\":\"Karachi\"}"

:: 6) Provider uploads CNIC front + back + selfie -> PENDING_REVIEW
curl -X POST http://localhost:8080/api/v1/providers/documents -H "Authorization: Bearer <accessToken>" -F "cnicFront=@cnic_front.jpg" -F "cnicBack=@cnic_back.jpg" -F "selfie=@selfie.jpg"

:: 7) Admin reviews (login as admin first to get an admin token)
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"phone\":\"03001234567\",\"password\":\"Admin@123\"}"
curl http://localhost:8080/api/v1/admin/providers?status=PENDING_REVIEW -H "Authorization: Bearer <adminAccessToken>"
curl http://localhost:8080/api/v1/admin/providers/1 -H "Authorization: Bearer <adminAccessToken>"

:: 8a) Approve -> provider becomes VERIFIED (APPROVED)
curl -X POST http://localhost:8080/api/v1/admin/providers/1/approve -H "Authorization: Bearer <adminAccessToken>"

:: 8b) or Reject with reason -> provider can fix & resubmit (step 5b/6 again)
curl -X POST http://localhost:8080/api/v1/admin/providers/1/reject -H "Authorization: Bearer <adminAccessToken>" -H "Content-Type: application/json" -d "{\"reason\":\"CNIC photo is blurred\"}"
```

## 7. Endpoint reference

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register/init` | public | phone+email+password → email OTP |
| POST | `/api/v1/auth/register/resend-otp` | public | resend (60 s cooldown, old OTP invalidated) |
| POST | `/api/v1/auth/verify-otp` | public | verify email code → activate + tokens |
| POST | `/api/v1/auth/select-role` | **JWT** | choose CUSTOMER / SERVICE_PROVIDER → fresh tokens |
| POST | `/api/v1/auth/login` | public | phone + password |
| POST | `/api/v1/auth/refresh` · `/logout` | public/JWT | token rotation / revoke |
| POST | `/api/v1/auth/forgot-password/init` · `/reset` | public | reset code by email → new password |
| GET | `/api/v1/users/me` | JWT | current user (role null until selected) |
| GET | `/api/v1/categories` | public | categories for the provider form |
| POST/GET | `/api/v1/customers/profile` | CUSTOMER | short profile form |
| POST/GET | `/api/v1/providers/profile` | SERVICE_PROVIDER | detailed profile form |
| POST/GET | `/api/v1/providers/documents` | SERVICE_PROVIDER | CNIC+selfie upload (multipart) / list |
| GET | `/api/v1/admin/providers[?status=]` | ADMIN | review queue |
| GET | `/api/v1/admin/providers/{id}` | ADMIN | submission detail |
| POST | `/api/v1/admin/providers/{id}/approve` | ADMIN | approve → VERIFIED |
| POST | `/api/v1/admin/providers/{id}/reject` | ADMIN | reject + reason |
| GET | `/api/v1/admin/providers/documents/{id}/file` | ADMIN | view CNIC/selfie image |
| POST | `/api/v1/admin/categories` | ADMIN | create category |

## 8. OTP security matrix (requirement → implementation)

| Requirement | Implementation |
|---|---|
| Random | `SecureRandom`, 6 digits (config `OTP_LENGTH`) |
| ~5 min expiry | `OTP_EXPIRY_MINUTES` (default 5) |
| Single-use | `verified=true` on success; verified codes never accepted again |
| Invalidated on resend | `superseded_at` set on all older codes when a new one is generated |
| Resend cooldown ~60 s | `OTP_RESEND_COOLDOWN_SECONDS` → HTTP 429 with wait time |
| Max wrong attempts | 3 per OTP (`OTP_MAX_ATTEMPTS`) → HTTP 429, must resend |
| No unlimited requests | per-IP rate limit: register/resend 10/h, verify 20/15 m |
| Never plaintext at rest | BCrypt hash in `auth_otp_codes.otp_hash` |
| Never in API responses | responses only carry expiry + resend timestamps |
| Never in production logs | OTP logged only when `OTP_MOCK_ENABLED=true` (default false) |
| Config, not hardcoded | every value is an env-overridable Spring property |

## 9. Production checklist

- [ ] Strong `JWT_SECRET` (64+ random chars), unique per environment
- [ ] Real MySQL user (not root) with privileges only on `smarttrust.*`
- [ ] `OTP_MOCK_ENABLED=false`, real SMTP credentials from a secret manager
- [ ] `UPLOAD_DIR` on a backed-up volume outside the app folder; serve only via the admin endpoint
- [ ] CORS restricted (`SecurityConfig.corsConfigurationSource` currently allows `*` for FYP)
- [ ] HTTPS behind a reverse proxy; rate-limit buckets tuned
