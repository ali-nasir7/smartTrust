> **UPDATED (v2):** The registration flow now uses EMAIL OTP + role selection + provider verification. See **SETUP-GUIDE.md** for current commands and endpoints.

# Windows Quick Start

1. Install PostgreSQL 15, create DB `smarttrust`
2. Run `src/main/resources/schema-auth.sql` in pgAdmin
3. Open CMD inside folder smarttrust-backend
4. Run `run.bat` or manually:
   mvn clean install
   mvn spring-boot:run -Dspring-boot.run.profiles=local
5. Open http://localhost:8080/swagger-ui.html

For MySQL: change DB_URL to jdbc:mysql://localhost:3306/smarttrust and driver.

Env vars can be set in Windows System Properties or via:
set DB_URL=...
