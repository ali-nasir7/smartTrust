# SmartTrust Auth Module — Manual Postman Testing Guide
### FYP Module 1 | Java 23 + Spring Boot 3.4.5 | PostgreSQL manual SQL

> **Goal:** Test all 6 auth APIs end-to-end manually, see OTP mock, JWT rotation, brute-force lock & rate limiting — like a production QA.

---

## 0. Prerequisites (Do This First)

### a) DB Running + Schema Executed
```sql
-- in pgAdmin Query Tool, run:
CREATE DATABASE smarttrust; -- if not exists
-- Then run entire file: src/main/resources/schema-auth.sql
```
Verify tables exist:
```sql
SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'auth_%' OR tablename='users';
-- Should show: users, auth_refresh_tokens, auth_otp_codes, auth_login_attempts
```

### b) App Running (Windows CMD)
```cmd
cd C:\Users\ESHOP\your-path\smarttrust-backend
set DB_URL=jdbc:postgresql://localhost:5432/smarttrust
set DB_USER=smarttrust
set DB_PASS=smarttrust123
set JWT_SECRET=dev-jwt-secret-must-be-at-least-64-chars-long-for-hs256-alg-0123456789AB
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Wait for log:
```
Started SmartTrustApplication in X seconds
JwtTokenProvider initialized issuer=smarttrust-api accessExpMs=900000
Tomcat started on port 8080
```

In another CMD, test health:
```cmd
curl http://localhost:8080/actuator/health
```
Should return `{"status":"UP"}`

Swagger UI (alternative to Postman): `http://localhost:8080/swagger-ui.html`

### c) Postman Setup

1. Open Postman Desktop
2. Import collection: `File -> Import -> docs/SmartTrust-Auth.postman_collection.json` (inside project)
3. If you don't have file, create new Collection manually named `SmartTrust Auth`
4. Create **Environment** named `SmartTrust Local`:
```
baseUrl = http://localhost:8080
accessToken = 
refreshToken =
phone = 03001234567
mockOtp = 
```
Select this environment top-right.

---

## 1. Understand Mock OTP (Important for FYP)

Because you don't have real SMS gateway, when `smarttrust.otp.mock-enabled=true` (default local):

1. OTP is **logged** in console:
```
🔐 OTP GENERATED phone=03001234567 purpose=REGISTRATION OTP=482910 expiresAt=2026-08-11T21:30:00Z
```
2. OTP is also **returned** in API response field `mockOtpForTesting` (ONLY dev, null in prod)

So for testing you can copy from response — no need to see logs, but logs help verify.

---

## 2. Flow A — Happy Path Registration (Full)

### Step A1: Register Init

**Request:** `POST {{baseUrl}}/api/v1/auth/register/init`

**Headers:** `Content-Type: application/json`

**Body:**
```json
{
  "phone": "03001234567",
  "password": "Test@1234",
  "role": "CUSTOMER",
  "fullName": "Ali Nasir"
}
```

**Validation to test later:**
- `role` must be CUSTOMER or SERVICE_PROVIDER (ADMIN blocked -> 403)
- `phone` regex: `03XXXXXXXXX` or `+923XXXXXXXXX` (try `03123` -> 400)
- `password` must have 1 upper, 1 lower, 1 digit, 8+ chars (`test1234` -> 400)

**Expected Success 201:**
```json
{
  "userId": 1,
  "phone": "03001234567",
  "role": "CUSTOMER",
  "message": "OTP sent to phone. Verify within 5 minutes.",
  "otpExpiresAt": "2026-08-11T21:35:00Z",
  "mockOtpForTesting": "482910"
}
```

**Postman Tests script (auto-save OTP):**
Go to request -> Tests tab -> paste:
```javascript
if (pm.response.code === 201) {
  const json = pm.response.json();
  pm.environment.set("phone", json.phone);
  pm.environment.set("mockOtp", json.mockOtpForTesting);
  console.log("Mock OTP:", json.mockOtpForTesting);
}
```

**DB Check:**
```sql
SELECT id, phone, role, status, phone_verified FROM users WHERE phone='03001234567';
-- status should be PENDING, phone_verified false

SELECT phone, purpose, expires_at, attempts, verified FROM auth_otp_codes WHERE phone='03001234567' ORDER BY created_at DESC LIMIT 1;
-- attempts 0, verified false, expires ~ now+5min
```

### Step A2: Verify OTP

**Request:** `POST {{baseUrl}}/api/v1/auth/verify-otp`

**Body:**
```json
{
  "phone": "03001234567",
  "otp": "{{mockOtp}}"
}
```
Or manually paste `482910`.

**Expected Success 200:**
```json
{
  "userId": 1,
  "phone": "03001234567",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyByYXcgcmVmcmVzaCB0b2tlbg...",
  "expiresIn": 900,
  "accessExpiresAt": "...",
  "refreshExpiresAt": "..."
}
```

**Save tokens automatically — Tests tab:**
```javascript
if (pm.response.code === 200) {
  const json = pm.response.json();
  pm.environment.set("accessToken", json.accessToken);
  pm.environment.set("refreshToken", json.refreshToken);
  console.log("Access Token saved");
}
```

**Check Response Headers:**
- `X-Correlation-Id`: UUID (our filter)
- `X-Rate-Limit-Remaining`: e.g., 19

**DB Check:**
```sql
SELECT status, phone_verified FROM users WHERE phone='03001234567'; -- ACTIVE, true
SELECT token_hash, revoked, expires_at FROM auth_refresh_tokens WHERE user_id=1; -- revoked false
```

### Step A3: Login (After verification)

Log out of current session mentally, then login again to test login flow.

**Request:** `POST {{baseUrl}}/api/v1/auth/login`

**Body:**
```json
{
  "phone": "03001234567",
  "password": "Test@1234",
  "deviceInfo": "Postman Windows"
}
```

**Expected 200** same structure as verify-otp, **NEW** refreshToken (old still valid? No, both valid until rotation, but we have 2 tokens now — that's okay. On refresh rotation old will be revoked)

**Save tokens** with same Tests script as above.

### Step A4: Use Protected Endpoint (Test JWT)

You haven't built user/me yet, but you can test Swagger `GET /actuator/health` is public, any authenticated endpoint will need token.

Create temp request to test auth filter:

**Request:** `GET {{baseUrl}}/api/v1/auth/health` (public) — should work without token.

Create a dummy protected request (will fail 403 until you add user module, but useful to test 401):
**Request:** `GET {{baseUrl}}/api/v1/users/me` (you haven't built, will 404, but you can still see JWT is validated before 404 if you add later)

Better: Try to call `GET {{baseUrl}}/api/v1/auth/health` with header `Authorization: Bearer {{accessToken}}` — should still 200 but proves header accepted.

### Step A5: Refresh Token Rotation (Critical Test)

**Request:** `POST {{baseUrl}}/api/v1/auth/refresh`

**Body:**
```json
{
  "refreshToken": "{{refreshToken}}"
}
```

**Expected 200** with NEW accessToken + NEW refreshToken.

**Tests script to update env:**
```javascript
if (pm.response.code === 200) {
  const json = pm.response.json();
  pm.environment.set("accessToken", json.accessToken);
  pm.environment.set("refreshToken", json.refreshToken);
  console.log("Rotated! New refresh saved");
}
```

**DB Check rotation:**
```sql
SELECT id, revoked FROM auth_refresh_tokens WHERE user_id=1 ORDER BY id DESC LIMIT 3;
-- First token (from verify-otp) should be still active? Actually after login we have 2 tokens. After refresh, the used token should be revoked=true, new one revoked=false
```

**Now test old refresh token reuse should FAIL:**
Send same refresh request again with OLD token (copy from Postman history) -> Expected **401** `AUTH_REFRESH_TOKEN_REVOKED` — proves rotation works. This is enterprise security!

### Step A6: Logout

**Request:** `POST {{baseUrl}}/api/v1/auth/logout`

**Body:**
```json
{
  "refreshToken": "{{refreshToken}}"
}
```

**Expected 204 No Content**

**Try refresh again with same token → 401 revoked** — proves logout.

---

## 3. Flow B — Error Cases (Must Test for FYP Demo)

### B1: Duplicate Phone Registration
- Call Register Init again with same phone `03001234567`
- Expected **409 CONFLICT** `AUTH_PHONE_ALREADY_EXISTS`

### B2: Invalid OTP
- Register new phone: `03001111111` / Test@1234
- Get mock OTP but send wrong OTP `000000` to verify-otp
- Expected **400** `AUTH_OTP_INVALID` + message `Attempts: 1/3`
- Repeat 2 more times wrong -> 3rd still 400, 4th attempt -> **429** `AUTH_OTP_MAX_ATTEMPTS`
- Then you must call register/init again to get new OTP

### B3: Expired OTP
- Hard to test manually waiting 5 min, but you can update DB:
```sql
UPDATE auth_otp_codes SET expires_at = NOW() - INTERVAL '1 minute' WHERE phone='03001111111';
```
- Then verify -> **400** `AUTH_OTP_EXPIRED`

### B4: Login with Wrong Password
- `POST /login` with phone `03001234567` password `Wrong@1234`
- Expected **401** `AUTH_INVALID_CREDENTIALS`
- Check DB:
```sql
SELECT phone, success, attempted_at FROM auth_login_attempts WHERE phone='03001234567' ORDER BY attempted_at DESC LIMIT 5;
-- success false row inserted
```

### B5: Brute Force Lock (5 fails → 15 min lock)
- Call login wrong password 5 times quickly for same phone
- 6th attempt (even with correct password) -> **423 LOCKED** `AUTH_ACCOUNT_LOCKED`
- Message: "Account locked due to too many failed attempts. Try again after 15 minutes."
- Check:
```sql
SELECT COUNT(*) FROM auth_login_attempts WHERE phone='03001234567' AND success=false AND attempted_at > NOW() - INTERVAL '15 minutes';
-- >=5
```
- Wait 15 min or clear table for testing: `DELETE FROM auth_login_attempts WHERE phone='03001234567';`

### B6: Login Before Verification (PENDING status)
- Register `03002222222` but DON'T verify OTP
- Call login with correct password -> **403** `AUTH_PHONE_NOT_VERIFIED`

### B7: Forgot Password Flow
**Step 1 Init:**
`POST /api/v1/auth/forgot-password/init`
Body: `{"phone":"03001234567"}`
Expected 200 + mock OTP

**Step 2 Reset:**
`POST /api/v1/auth/forgot-password/reset`
Body:
```json
{
  "phone": "03001234567",
  "otp": "{{mockOtp}}",
  "newPassword": "NewPass@1234"
}
```
Expected 200

**Verify:**
- Login with old password `Test@1234` -> 401 fail
- Login with new `NewPass@1234` -> 200 success
- Check old refresh tokens revoked: `SELECT revoked FROM auth_refresh_tokens WHERE user_id=1;` -> all true except new one from last login

### B8: Rate Limiting (Bucket4j)
- Rapidly hit `POST /auth/login` 6 times within 1 minute from same IP (use wrong passwords to avoid lock interfering)
- On 6th request, expect **429** with header `X-Rate-Limit-Retry-After-Seconds`
- Body: `RATE_LIMIT_EXCEEDED`

---

## 4. Postman Environment Auto-Setup (Copy-Paste)

In Postman, collection -> Edit -> Tests (for whole collection) -> add:

```javascript
// Auto set tokens if response has accessToken
if (pm.response.json() && pm.response.json().accessToken) {
  const json = pm.response.json();
  pm.environment.set("accessToken", json.accessToken);
  if (json.refreshToken) pm.environment.set("refreshToken", json.refreshToken);
}
```

In each request -> **Headers** -> add:
```
Authorization: Bearer {{accessToken}}   // Only for future protected endpoints, NOT for auth/* which are public
X-Correlation-Id: {{$guid}}  // optional, tests filter
```

Actually for auth/* endpoints you do NOT need Authorization header (they're public). Only future modules need it.

---

## 5. cURL Quick Cheat for Windows CMD

If Postman not working, use curl.exe (Windows has curl):

```cmd
curl -X POST http://localhost:8080/api/v1/auth/register/init -H "Content-Type: application/json" -d "{\"phone\":\"03001234567\",\"password\":\"Test@1234\",\"role\":\"CUSTOMER\",\"fullName\":\"Ali Nasir\"}"

curl -X POST http://localhost:8080/api/v1/auth/verify-otp -H "Content-Type: application/json" -d "{\"phone\":\"03001234567\",\"otp\":\"123456\"}"

curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"phone\":\"03001234567\",\"password\":\"Test@1234\"}"

REM Save refresh from previous response and use:
curl -X POST http://localhost:8080/api/v1/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"YOUR_REFRESH\"}"

curl -X POST http://localhost:8080/api/v1/auth/logout -H "Content-Type: application/json" -d "{\"refreshToken\":\"YOUR_REFRESH\"}"
```

---

## 6. DB Verification Queries (Keep Open in pgAdmin)

After each step, run:

```sql
-- Users status
SELECT id, phone, role, status, phone_verified, created_at FROM users ORDER BY id DESC;

-- OTP audit
SELECT id, phone, purpose, attempts, verified, expires_at, created_at FROM auth_otp_codes ORDER BY created_at DESC LIMIT 5;

-- Refresh tokens lifecycle
SELECT id, user_id, LEFT(token_hash,20) as hash_preview, revoked, expires_at FROM auth_refresh_tokens ORDER BY id DESC LIMIT 5;

-- Brute force attempts
SELECT phone, ip_address, success, attempted_at FROM auth_login_attempts ORDER BY attempted_at DESC LIMIT 10;
```

---

## 7. Expected Final State After Full Happy Path

- User `03001234567`: status ACTIVE, phone_verified true
- At least 2-3 refresh tokens: one revoked after rotation, one active
- auth_otp_codes: at least 1 verified true
- auth_login_attempts: success true for login attempts

---

## 8. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `401 Invalid JWT` on /auth/verify-otp | You sent Authorization header but verify-otp is public, old token expired? | Remove Authorization header for auth/*, use raw body only |
| `Handshake_failure` Maven build fails | Your sandbox Java 11, needs Java 23 | Your Windows Java 23 will work, ignore sandbox build fail |
| `relation "users" does not exist` | Schema not executed | Run schema-auth.sql manually |
| `Phone already exists` 409 but you deleted row | Unique index where status != DELETED, but old row still ACTIVE | DELETE from users or set status DELETED, or use new phone |
| `OTP expired` immediate | Server time vs DB time mismatch | Check `SELECT NOW();` and app log expiry, ensure both use UTC |
| `CorrelationId not shown` | Filter not registered? | Check WebConfig does register CorrelationIdFilter |
| `429 Rate Limit` even first request | Bucket4j cache persists in-memory, restart app | Restart app `mvn spring-boot:run` to clear cache |
| Forgot password old tokens still work | You didn't revoke? | Our code revokesAllByUserId on reset — check logs |

---

## 9. Demo Script for FYP Supervisor (5 min)

1. Show `schema-auth.sql` manual run — explain why raw SQL (DBA review) vs Flyway future
2. Show Swagger UI `http://localhost:8080/swagger-ui.html` — 6 endpoints documented
3. Postman: Register Init -> Show mock OTP in response + console log
4. Verify OTP -> Show JWT + refresh issued
5. Show DB row ACTIVE
6. Login wrong password 5 times -> Show 423 LOCKED
7. Show refresh rotation -> reuse old refresh -> 401 revoked (enterprise security)
8. Logout -> 204
9. Show `ApiError` uniform format on validation fail (try phone `123`)
10. Show headers `X-Correlation-Id` and `X-Rate-Limit-Remaining`

That's professional enterprise auth demo.

---

**Ready for Module 2?** After this passes, we build `Customer` + `Provider (CNIC AES-GCM + PostGIS)` modules reusing this auth.
