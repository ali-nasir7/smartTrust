-- ============================================================================
-- SMARTTRUST — AUTH MODULE SQL SCHEMA
-- DEFAULT: MySQL 8.0+ (as requested) | Optional: PostgreSQL version below
-- Run this file manually in MySQL Workbench / DBeaver / phpMyAdmin
-- No Flyway — manual SQL as requested
-- ============================================================================

-- ===================== MySQL Version (Primary - Use This) =====================
CREATE DATABASE IF NOT EXISTS smarttrust CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE smarttrust;

-- USERS Table (core identity)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  email VARCHAR(190) NULL,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('CUSTOMER','SERVICE_PROVIDER','ADMIN') NOT NULL,
  status ENUM('PENDING','ACTIVE','SUSPENDED','BANNED','DELETED') NOT NULL DEFAULT 'PENDING',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_users_phone (phone),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AUTH REFRESH TOKENS (opaque token SHA-256 hashed)
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_ip VARCHAR(45),
  device_info VARCHAR(255),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_expires (expires_at),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AUTH OTP CODES (6-digit OTP BCrypt hashed)
CREATE TABLE IF NOT EXISTS auth_otp_codes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  otp_hash VARCHAR(255) NOT NULL,
  purpose ENUM('REGISTRATION','LOGIN','FORGOT_PASSWORD') NOT NULL,
  expires_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_otp_phone_purpose (phone, purpose, created_at),
  KEY idx_otp_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AUTH LOGIN ATTEMPTS (brute-force protection)
CREATE TABLE IF NOT EXISTS auth_login_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  ip_address VARCHAR(45),
  success BOOLEAN NOT NULL,
  attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_login_phone_time (phone, attempted_at),
  KEY idx_login_ip_time (ip_address, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional: Insert test admin manually after you have BCrypt hash
-- Password 'Admin@123' BCrypt hash example - generate via your app PasswordEncoder
-- INSERT INTO users(phone, password_hash, role, status, phone_verified) VALUES ('03001234567', '$2a$12$...', 'ADMIN', 'ACTIVE', true);

-- ============================================================================
-- ===================== PostgreSQL Version (Alternative - If needed) =========
-- Uncomment below if you switch to PostgreSQL later
-- ============================================================================
/*
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email VARCHAR(190),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('CUSTOMER','SERVICE_PROVIDER','ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','BANNED','DELETED')),
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON users(phone) WHERE status != 'DELETED';
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email ON users(email) WHERE email IS NOT NULL AND email <> '';
CREATE INDEX IF NOT EXISTS idx_users_role_status ON users(role, status);

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_ip VARCHAR(45),
    device_info VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON auth_refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_expires ON auth_refresh_tokens(expires_at);

CREATE TABLE IF NOT EXISTS auth_otp_codes (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('REGISTRATION','LOGIN','FORGOT_PASSWORD')),
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_otp_phone_purpose_created ON auth_otp_codes(phone, purpose, created_at DESC);

CREATE TABLE IF NOT EXISTS auth_login_attempts (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_login_phone_time ON auth_login_attempts(phone, attempted_at DESC);
*/
