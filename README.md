# SmartTrust Backend — Auth (Email OTP) + Customer/Provider Onboarding + Admin Verification

**Stack:** Java 23 + Spring Boot 3.4.x + MySQL 8 + Spring Security + JWT + BCrypt12 + Caffeine Rate Limiting + SMTP Email OTP + Swagger OpenAPI

**v2 highlights** (see `SETUP-GUIDE.md` for the full walk-through):

- Registration takes **phone + email + password**; the **OTP is emailed** (branded *SmartTrust — Verify Your Email*).
- OTP: SecureRandom, 5-min expiry, single-use, 60-s resend cooldown, max 3 attempts, old code invalidated on resend, BCrypt-hashed at rest, **never in API responses or production logs** (all values env-configurable).
- After verifying, the user **selects a role**: `CUSTOMER` or `SERVICE_PROVIDER` (`POST /api/v1/auth/select-role`, returns fresh JWT).
- **Customer** completes a short form (`/api/v1/customers/profile`).
- **Service Provider** completes a detailed form (`/api/v1/providers/profile`), uploads **CNIC front + back + selfie** (`/api/v1/providers/documents`) → status `PENDING_REVIEW`.
- **Admin** reviews (`/api/v1/admin/providers`), **approves** (→ `APPROVED`/Verified) or **rejects with a reason** (provider may resubmit).
- Gmail SMTP delivery via App Password; **credentials only via env/`.env`** (see `.env.example`).

## 📁 Folder Structure (Modular Monolith)

```
com.smarttrust
├── SmartTrustApplication.java
├── common/
│   ├── config/         -> SecurityConfig, WebConfig, CacheConfig, OpenApiConfig, JacksonConfig, FilterConfig
│   ├── security/       -> JwtTokenProvider, JwtAuthenticationFilter, RateLimitService/Filter, UserPrincipal,
│   │                      CustomUserDetailsService, SecurityUtils
│   ├── mail/           -> EmailService (branded OTP email, SMTP via env)
│   ├── storage/        -> FileStorageService (CNIC/selfie uploads, type+magic-byte checks)
│   ├── exception/      -> ErrorCode, BusinessException, ApiError, GlobalExceptionHandler
│   ├── audit/ | validation/ | util/
└── modules/
    ├── auth/           -> register (email OTP) / resend / verify / select-role / login / refresh / logout / forgot-password
    ├── user/           -> User entity (phone+email+role nullable), /users/me, AdminUserSeeder
    ├── customer/       -> short profile form (customer_profiles)
    └── provider/       -> categories, provider profiles, CNIC/selfie documents, admin review
```

## 🗄️ Database (MySQL 8, manual SQL — no Flyway per project convention)

- **Fresh install:** run `src/main/resources/schema-auth.sql`
- **Upgrade existing v1 DB:** run `src/main/resources/db/upgrade-v2.sql`

Tables: `users` (role nullable until selection, full_name added) · `auth_otp_codes` (+email, superseded_at) ·
`auth_refresh_tokens` · `auth_login_attempts` · `service_categories` (seeded) · `customer_profiles` ·
`service_provider_profiles` · `provider_documents`.

## 🔐 v2 Registration Flow

```
POST /api/v1/auth/register/init   {phone, email, password, fullName?}
        └─> user PENDING, OTP emailed (never in response)
POST /api/v1/auth/register/resend-otp   {phone}          (60s cooldown, old OTP dies)
POST /api/v1/auth/verify-otp      {phone, otp}           -> ACTIVE + tokens (role may be null)
POST /api/v1/auth/select-role     {role}    [JWT]        -> role set + fresh tokens
   ├─ CUSTOMER        -> POST /api/v1/customers/profile  {fullName, address, city}     done
   └─ SERVICE_PROVIDER-> POST /api/v1/providers/profile  {fullName, categoryId, experienceYears, skills[], bio, address, city}
                        -> POST /api/v1/providers/documents  multipart cnicFront/cnicBack/selfie
                           └─> PENDING_REVIEW
Admin: GET /api/v1/admin/providers?status=PENDING_REVIEW
       POST .../approve  |  POST .../reject {reason}  (resubmission supported)
```

## 🚀 Run (Windows, Java 23.0.1 + Maven 3.9.9)

1. MySQL: run the schema SQL (above) in Workbench.
2. `copy .env.example .env` and fill DB/SMTP/admin values (Gmail App Password — see SETUP-GUIDE.md §2).
3. `mvn clean compile`
4. `mvn spring-boot:run -Dspring-boot.run.profiles=local`
5. Swagger: http://localhost:8080/swagger-ui.html · Health: `/actuator/health`

## 🛡️ Security Features (carried over / extended from v1)

BCrypt12 passwords · hashed OTPs · opaque SHA-256 refresh tokens with rotation · JWT HS256 15-min access tokens ·
brute-force lockout (5 fails/15 min → 423) · per-IP rate limits (login 5/15m, register+resend 10/h, verify 20/15m,
forgot 5/30m) · correlation IDs · uniform `ApiError` responses · document files stored outside the webroot with
generated names, extension+magic-byte validation and admin-only serving.

## 🔜 Next Modules

Marketplace requests/hyperlocal matching, Trust Score, AI ranking, notifications — to be built on the
`users`/`customer_profiles`/`service_provider_profiles` base above.

## 🧑‍💻 Author
Ali Nasir — SmartTrust FYP Backend — Hamdard University
