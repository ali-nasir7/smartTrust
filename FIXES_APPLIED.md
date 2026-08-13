# Fixes Applied - 2026-08-11

## Issue 1: `io.bucket4j cannot be resolved`

**Cause:** Bucket4j dependency needs download, your VS Code Java extension couldn't resolve offline.

**Fix:** Removed Bucket4j entirely. Replaced with **pure Java + Caffeine** rate limiting (No external lib).

Files changed:
- `pom.xml` -> Removed `bucket4j-core` dependency, updated Spring Boot to 3.4.13 (latest patch as suggested by your IDE)
- `RateLimitService.java` -> Rewrote using Caffeine Cache + SimpleBucket (fixed window, thread-safe)
- `RateLimitFilter.java` -> Updated to use new `RateLimitService.RateLimitResult` (consumed, remaining, retryAfterSeconds)

**Result:** Zero external rate limit lib, same logic: LOGIN 5/15min, REGISTER 10/60min, OTP 20/15min, FORGOT 5/30min. Headers `X-Rate-Limit-Remaining` still work.

## Issue 2: Want MySQL (sql) not PostgreSQL

**You said:** "i want to use sql not postgre"

**Fix:** Switched defaults to MySQL 8.0:

- `application.yml` -> Default datasource now:
  ```
  jdbc:mysql://localhost:3306/smarttrust?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
  driver: com.mysql.cj.jdbc.Driver
  dialect: MySQLDialect
  username: root
  password: root123 (set via env DB_PASS)
  ```
  PostgreSQL moved to profile `postgres`: run with `-Dspring-boot.run.profiles=postgres` to switch back.

- `schema-auth.sql` -> MySQL version now primary (active), PostgreSQL commented alternative.

- Entities remain compatible: `GenerationType.IDENTITY` works for both MySQL and PostgreSQL, `Instant` maps to DATETIME (MySQL) or TIMESTAMPTZ (Postgres).

## How to Run Now (MySQL):

1. Install MySQL 8.0, create DB:
```sql
CREATE DATABASE smarttrust CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

2. Run `src/main/resources/schema-auth.sql` in MySQL Workbench (it includes `USE smarttrust;`)

3. In Windows CMD:
```cmd
set DB_URL=jdbc:mysql://localhost:3306/smarttrust?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
set DB_USER=root
set DB_PASS=your_mysql_root_password
set JWT_SECRET=dev-jwt-secret-must-be-at-least-64-chars-long-for-hs256-alg-0123456789AB
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

If you still want Postgres later:
```cmd
set DB_URL=jdbc:postgresql://localhost:5432/smarttrust
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Verify Fix:

After `mvn clean compile`, your IDE errors should disappear.

In VS Code:
- Ctrl+Shift+P -> "Java: Clean Java Language Server Workspace"
- Then reload window
- Or delete `.vscode` cache, and run `mvn dependency:resolve`

No more bucket4j imports.

## Extra:

Updated `pom.xml` Spring Boot 3.4.5 -> 3.4.13 as your IDE suggested newer patch available (still supports Java 23).

All other auth logic unchanged (OTP hash BCrypt, refresh SHA-256, JWT HS256, brute-force lock, correlationId).

