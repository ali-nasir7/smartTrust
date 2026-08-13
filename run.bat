@echo off
REM SmartTrust Auth Module - Run Script for Windows
REM Requires Java 23 and Maven 3.9.9

echo === SmartTrust Auth Module ===
echo Checking Java version...
java --version
echo.
echo Checking Maven version...
mvn --version
echo.

echo Setting environment variables (dev defaults)...
set DB_URL=jdbc:postgresql://localhost:5432/smarttrust
set DB_USER=smarttrust
set DB_PASS=smarttrust123
set JWT_SECRET=dev-jwt-secret-must-be-at-least-64-chars-long-for-hs256-alg-0123456789AB
set CNIC_ENCRYPTION_KEY=dev-aes-key-32-chars-long-123456789

echo Building project...
mvn clean compile

echo Running with local profile...
mvn spring-boot:run -Dspring-boot.run.profiles=local

pause
