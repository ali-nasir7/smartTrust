@echo off
REM SmartTrust Backend v2 - Run Script for Windows (Java 23 + Maven 3.9.9)
REM Prereqs: MySQL 8 running, schema executed, .env file created (see .env.example)

echo === SmartTrust Backend v2 (Email OTP + Provider Verification) ===
java --version
mvn --version

if not exist .env (
    echo WARNING: .env not found. Copy .env.example to .env and fill values.
    pause
    exit /b 1
)

echo Building...
call mvn clean compile
if %errorlevel% neq 0 (
    echo BUILD FAILED
    pause
    exit /b 1
)

echo Starting (local profile)...
call mvn spring-boot:run -Dspring-boot.run.profiles=local
pause
