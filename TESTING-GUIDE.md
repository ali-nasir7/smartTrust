# SmartTrust v2 — Complete Testing Guide (Swagger UI)

Test everything in the exact order below. Each phase depends on the previous one.
Base URL: `http://localhost:8080` · Swagger: `http://localhost:8080/swagger-ui.html`

---

# PART A — Configure like PRODUCTION (before testing)

Production mode = **real emails, real OTP rules, strong secrets, no dev shortcuts**. Do this once.

## A1. MySQL: dedicated DB user (not root)

Run in MySQL Workbench:

```sql
CREATE USER 'smarttrust'@'localhost' IDENTIFIED BY 'Stg0ng!Pass99';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON smarttrust.* TO 'smarttrust'@'localhost';
FLUSH PRIVILEGES;
```

## A2. Gmail App Password (for real OTP emails)

1. Google account → **Security** → turn ON **2-Step Verification**.
2. Security → search **"App passwords"** → Create → name `SmartTrust` → copy the **16-character** password (spaces hata kar).

## A3. Generate a strong JWT secret

PowerShell (project folder me):

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | % {[char]$_})
```

## A4. Final production-like `.env`

```env
DB_URL=jdbc:mysql://localhost:3306/smarttrust?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=smarttrust
DB_PASS=Stg0ng!Pass99

JWT_SECRET=<A3 ka 64-char output>

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=yourgmail@gmail.com
SMTP_PASSWORD=abcdefghijklmnop
MAIL_FROM=yourgmail@gmail.com
MAIL_FROM_NAME=SmartTrust

OTP_MOCK_ENABLED=false
OTP_EXPIRY_MINUTES=5
OTP_LENGTH=6
OTP_MAX_ATTEMPTS=3
OTP_RESEND_COOLDOWN_SECONDS=60

ADMIN_PHONE=03001234567
ADMIN_EMAIL=admin@smarttrust.pk
ADMIN_PASSWORD=Admin@123

UPLOAD_DIR=./uploads
```

## A5. Restart & verify startup

```cmd
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Console me confirm karo:
- `ADMIN account created phone=03001234567` (pehli dafa)
- `RateLimitService initialized`, `JwtTokenProvider initialized`
- koi `OTP email delivery FAILED` nahi (SMTP theek hai)
- `Started SmartTrustApplication`

## A6. How to use Swagger Authorize (IMPORTANT)

1. Swagger kholo → top right **Authorize (🔒)** button.
2. Value me SIRF token paste karo (bina `Bearer ` prefix ke — Swagger khud lagata hai).
3. Token tab change karo jab naya user/login ho (admin vs provider vs customer).

---

# PART B — TEST HIERARCHY (test in this exact order)

```
Phase 0  Sanity            health + categories
Phase 1  Admin             login → token
Phase 2  Customer signup   register → email OTP → verify → select-role(CUSTOMER) → short form
Phase 3  Provider signup   register → verify → select-role(SERVICE_PROVIDER) → detailed form
Phase 4  Provider docs     CNIC front/back/selfie upload → PENDING_REVIEW
Phase 5  Admin review      queue → detail → view image → APPROVE → verify status
Phase 6  Reject + resubmit → REJECTED (reason) → re-upload → APPROVE
Phase 7  Token lifecycle  refresh rotation / reuse fail / logout
Phase 8  Forgot password   email OTP → reset → old refresh dead
Phase 9  Security negative tests (401/403/409/423/429 table)
```

---

## Phase 0 — Sanity (no token)

| # | Test | Endpoint | Expected |
|---|---|---|---|
| 0.1 | Health | `GET /actuator/health` | 200 `{"status":"UP"...}` |
| 0.2 | Auth health | `GET /api/v1/auth/health` | 200 string |
| 0.3 | Categories | `GET /api/v1/categories` | 200, 15 items (Electrician…) |

## Phase 1 — Admin login

| # | Test | Endpoint | Body | Expected |
|---|---|---|---|---|
| 1.1 | Login admin | `POST /api/v1/auth/login` | `{"phone":"03001234567","password":"Admin@123"}` | 200 + `accessToken` (save as **TOKEN-ADMIN**) |

## Phase 2 — Customer signup (email OTP flow)

| # | Test | Endpoint | Body / Action | Expected |
|---|---|---|---|---|
| 2.1 | Register | `POST /api/v1/auth/register/init` | `{"phone":"03011111111","email":"<real inbox>","password":"Test@1234","fullName":"Ali Raza"}` | **201** + `maskedEmail` (`ali***@gmail.com`), `otpExpiresAt`, `resendAvailableAt`. **Response me OTP NAHI hona chahiye** ✔ |
| 2.2 | Check email | apna inbox | subject **"SmartTrust — Verify Your Email"** | Branded email + 6-digit code + "expires in 5 minutes" + security notice |
| 2.3 | Resend cooldown | turant `POST /api/v1/auth/register/resend-otp` `{"phone":"03011111111"}` | | **429** `Please wait N seconds…` (AUTH_OTP_RESEND_COOLDOWN) |
| 2.4 | Wrong OTP | `POST /api/v1/auth/verify-otp` | `{"phone":"03011111111","otp":"000000"}` | **400** `Invalid OTP. Attempts: 1/3` |
| 2.5 | 3rd wrong OTP | same, 2 more times | | 3rd try → **429** `Maximum verification attempts reached` |
| 2.6 | Resend after max | `POST .../resend-otp` (60s baad) | | 200 → naya code email pe, purana code DEAD (check: purana code se verify → invalid) |
| 2.7 | Verify correct | `POST /api/v1/auth/verify-otp` | email ka naya code | **200** + `accessToken`,`refreshToken`, `"role": null` (abhi select nahi kiya) |
| 2.8 | Who am I | `GET /api/v1/users/me` (Authorize = token) | | 200: role `null`, `emailVerified: true`, status `ACTIVE` |
| 2.9 | Select role | `POST /api/v1/auth/select-role` | `{"role":"CUSTOMER"}` | **200** + FRESH tokens (role ab JWT me hai) — naya accessToken save karo (**TOKEN-CUSTOMER**) |
| 2.10 | Repeat select | same endpoint dobara | | **409** AUTH_ROLE_ALREADY_SELECTED |
| 2.11 | Customer form | `POST /api/v1/customers/profile` | `{"fullName":"Ali Raza","address":"House 12, Street 4","city":"Rawalpindi"}` | **201** |
| 2.12 | Get profile | `GET /api/v1/customers/profile` | | 200 saved values |

## Phase 3 — Provider signup

Naya user (different phone + different/same inbox):
- phone `03022222222`, email apna, password `Test@1234`
- 3.1 register/init → 3.2 verify-otp (email se) → 3.3 `select-role` `{"role":"SERVICE_PROVIDER"}` → token = **TOKEN-PROVIDER**

| # | Test | Endpoint | Body | Expected |
|---|---|---|---|---|
| 3.4 | Detailed form | `POST /api/v1/providers/profile` | `{"fullName":"Kamran Iqbal","categoryId":1,"experienceYears":5,"skills":["Wiring","Fan install"],"bio":"Certified electrician","address":"Plaza 9, Saddar","city":"Karachi"}` | **201**, `verificationStatus: "NOT_SUBMITTED"` |
| 3.5 | Bad category | `categoryId: 9999` dobara | | **400** CATEGORY_NOT_FOUND |
| 3.6 | Get profile | `GET /api/v1/providers/profile` | | 200 + status NOT_SUBMITTED |

## Phase 4 — CNIC + selfie upload (PENDING_REVIEW)

| # | Test | Endpoint | Action | Expected |
|---|---|---|---|---|
| 4.1 | Upload docs | `POST /api/v1/providers/documents` | Swagger me 3 file pickers: `cnicFront`, `cnicBack`, `selfie` — JPG/PNG ≤5MB | **202**, `verificationStatus: "PENDING_REVIEW"` |
| 4.2 | Missing one | sirf 2 files ke saath (ek blank chhoro) | | **400** PROVIDER_DOCS_REQUIRED |
| 4.3 | Wrong type | `.txt` ya `.pdf` file | | **400** DOCUMENT_INVALID_TYPE |
| 4.4 | Oversize | 6MB image | | **413** FILE_TOO_LARGE |
| 4.5 | My docs | `GET /api/v1/providers/documents` | | 200 — 3 entries (metadata only) |

## Phase 5 — Admin review → APPROVE

Authorize = **TOKEN-ADMIN**:

| # | Test | Endpoint | Expected |
|---|---|---|---|
| 5.1 | Queue | `GET /api/v1/admin/providers?status=PENDING_REVIEW` | 200, provider listed with docs metadata |
| 5.2 | Detail | `GET /api/v1/admin/providers/{id}` | 200 — profile + phone + email + 3 documents |
| 5.3 | View CNIC | `GET /api/v1/admin/providers/documents/{docId}/file` | 200 — image render ho (browser me) |
| 5.4 | Approve | `POST /api/v1/admin/providers/{id}/approve` | 200 — `verificationStatus: "APPROVED"`, `verifiedAt` set |
| 5.5 | Approve dobara | same | **409** PROVIDER_INVALID_STATUS_TRANSITION |
| 5.6 | Provider side | TOKEN-PROVIDER → `GET /api/v1/providers/profile` | `APPROVED` + `rejectionReason: null` ✔ |

## Phase 6 — REJECT + resubmission

Provider (TOKEN-PROVIDER): docs **dobara** upload karo (Phase 4.1) → phir se `PENDING_REVIEW`.
Admin (TOKEN-ADMIN):

| # | Test | Endpoint | Body | Expected |
|---|---|---|---|---|
| 6.1 | Reject | `POST /api/v1/admin/providers/{id}/reject` | `{"reason":"CNIC photo is blurred"}` | 200 — `REJECTED` + reason |
| 6.2 | Provider sees | `GET /api/v1/providers/profile` (TOKEN-PROVIDER) | | `REJECTED` + `rejectionReason: "CNIC photo is blurred"` |
| 6.3 | Resubmit | clear photos ke saath `POST /api/v1/providers/documents` | | **202** → `PENDING_REVIEW`, reason cleared |
| 6.4 | Approve | admin approve | | 200 `APPROVED` ✔ resubmission works |

## Phase 7 — Token lifecycle

| # | Test | Steps | Expected |
|---|---|---|---|
| 7.1 | Refresh rotation | `POST /api/v1/auth/refresh` `{"refreshToken":"<current>"}` | 200 — NAYA pair; purana refresh ab invalid |
| 7.2 | Old refresh reuse | purane refreshToken se dobara refresh | **401** AUTH_REFRESH_TOKEN_REVOKED |
| 7.3 | Logout | `POST /api/v1/auth/logout` naye refreshToken se | **204** |
| 7.4 | Refresh after logout | logout wala token se refresh | **401** |
| 7.5 | Logout idempotent | galat token se logout | phir bhi **204** (error leak nahi) |

Note: accessToken logout ke baad bhi 15 min chalta hai (short-lived by design) — production me yehi pattern hota hai.

## Phase 8 — Forgot password

| # | Test | Endpoint | Expected |
|---|---|---|---|
| 8.1 | Init | `POST /api/v1/auth/forgot-password/init` `{"phone":"03011111111"}` | 200 + email me reset code |
| 8.2 | Reset | `POST /api/v1/auth/forgot-password/reset` `{"phone":"03011111111","otp":"<code>","newPassword":"NewTest@1234"}` | 200 |
| 8.3 | Old refresh dead | 8.1 se pehle wala refresh token use karo | **401** (saare revoked) |
| 8.4 | New login | login `NewTest@1234` se | 200 ✔ |

## Phase 9 — Security matrix (negative tests)

| # | Test | How | Expected |
|---|---|---|---|
| 9.1 | No token | `GET /api/v1/users/me` bina Authorize | **401** |
| 9.2 | Wrong role | TOKEN-CUSTOMER se `POST /api/v1/providers/profile` | **403** |
| 9.3 | Wrong role 2 | TOKEN-PROVIDER se `GET /api/v1/customers/profile` | **403** |
| 9.4 | Not admin | TOKEN-PROVIDER se `/api/v1/admin/providers` | **403** |
| 9.5 | Tampered JWT | accessToken me 1 char change | **401** |
| 9.6 | Duplicate phone | register/init same phone | **409** AUTH_PHONE_ALREADY_EXISTS |
| 9.7 | Duplicate email | register/init same email, nayi phone | **409** AUTH_EMAIL_ALREADY_EXISTS |
| 9.8 | Weak password | `"password":"test123"` | **400** validation |
| 9.9 | Bad phone format | `"0301123"` | **400** validation |
| 9.10 | Brute-force lock | login 5 baar galat password | 5th → **423** AUTH_ACCOUNT_LOCKED |
| 9.11 | Lock expiry | 15 min wait karo | login phir se 200/401 (locked nahi) |
| 9.12 | Register rate limit | register/init 11 baar (alag phones, <60min) | 11th → **429** RATE_LIMIT_EXCEEDED + headers `X-Rate-Limit-*` |
| 9.13 | OTP in response? | 2.1 ka response dekho | OTP kahin nahi ✔ |
| 9.14 | OTP in logs? | console dekho (mock=false) | OTP print NAHI hota ✔ |

---

# PART C — Quick expected-status cheat sheet

| Endpoint | Success | Common errors |
|---|---|---|
| register/init | 201 | 409 dup phone/email · 400 validation · 429 rate limit |
| resend-otp | 200 | 429 cooldown (60s) · 404 no user · 409 already verified |
| verify-otp | 200 | 400 invalid · 400 expired · 429 max attempts · 404 no OTP |
| select-role | 200 | 401 no token · 403 not ACTIVE/admin role · 409 already selected |
| login | 200 | 401 creds · 403 PENDING/suspended · 423 locked |
| refresh | 200 | 401 invalid/revoked/expired |
| logout | 204 | (never errors) |
| customers/profile | 201/200 | 401 · 403 wrong role · 404 not completed (GET) |
| providers/profile | 201/200 | 400 bad category · 401 · 403 |
| providers/documents | 202 | 400 missing/profile incomplete · 400 wrong type · 413 too large |
| admin/providers* | 200 | 403 not admin · 404 not found · 409 wrong state |
| categories | 200 | — |

# PART D — Test data summary

| Persona | Phone | Password | Notes |
|---|---|---|---|
| Admin | 03001234567 | Admin@123 | .env se auto-created |
| Customer | 03011111111 | Test@1234 → NewTest@1234 (Phase 8 ke baad) | |
| Provider | 03022222222 | Test@1234 | CNIC selfies: koi bhi 3 JPG/PNG ≤5MB |

# PART E — Common problems while testing

| Symptom | Fix |
|---|---|
| Email nahi aaya | Spam/junk folder; SMTP_PASSWORD me 16-char App Password (spaces ke bina); 2FA ON hai?; console me `OTP email delivery FAILED` dekho |
| `Could not get JDBC Connection` | MySQL chal nahi raha / DB_PASS galat |
| 429 har request pe | Rate limit window me ho — thora ruk jao ya server restart (in-memory buckets reset) |
| 423 locked | 15 min ka lock — dobara test ke liye: `DELETE FROM auth_login_attempts WHERE phone='...';` MySQL me |
| 401 har secured call pe | Authorize me `Bearer ` prefix ke saath paste kiya? — sirf token paste karo |
| Upload 500 | UPLOAD_DIR folder writable hai? console me `FileStorageService baseDir=...` dekho |
| Sab kuch reset karna ho | MySQL: `DROP DATABASE smarttrust;` phir `schema-auth.sql` + restart (admin phir se ban jayega) |
