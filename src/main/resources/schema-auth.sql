-- ============================================================================
-- SMARTTRUST — AUTH + USER + PROVIDER VERIFICATION SCHEMA (MySQL 8.0+ PRIMARY)
-- FRESH INSTALL: run this whole file in MySQL Workbench / DBeaver.
-- EXISTING INSTALL (v1 auth-only): run db/upgrade-v2.sql instead.
-- No Flyway — manual, DBA-reviewed SQL (per project convention).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS smarttrust CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE smarttrust;

-- USERS Table (core identity: phone + email, BCrypt password)
-- v2: role is now NULLABLE (role is selected AFTER email verification),
--     full_name added (optional at registration, profile forms are authoritative).
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  email VARCHAR(190) NULL,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  full_name VARCHAR(120) NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('CUSTOMER','SERVICE_PROVIDER','ADMIN') NULL DEFAULT NULL,
  status ENUM('PENDING','ACTIVE','SUSPENDED','BANNED','DELETED') NOT NULL DEFAULT 'PENDING',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_users_phone (phone),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AUTH REFRESH TOKENS (opaque token SHA-256 hashed, rotation)
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

-- AUTH OTP CODES (OTP BCrypt hashed — never plaintext)
-- v2: email column (delivery target for email OTP), superseded_at column
--     (old OTP invalidated the moment a new one is generated).
CREATE TABLE IF NOT EXISTS auth_otp_codes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(190) NULL,
  otp_hash VARCHAR(255) NOT NULL,
  purpose ENUM('REGISTRATION','LOGIN','FORGOT_PASSWORD') NOT NULL,
  expires_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  superseded_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_otp_phone_purpose (phone, purpose, created_at),
  KEY idx_otp_email_purpose (email, purpose, created_at),
  KEY idx_otp_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AUTH LOGIN ATTEMPTS (brute-force protection audit)
CREATE TABLE IF NOT EXISTS auth_login_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  ip_address VARCHAR(45),
  success BOOLEAN NOT NULL,
  attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_login_phone_time (phone, attempted_at),
  KEY idx_login_ip_time (ip_address, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- v2 MODULES: SERVICE CATEGORIES / CUSTOMER PROFILES / PROVIDER PROFILES
--            / PROVIDER DOCUMENTS (CNIC + selfie verification)
-- ============================================================================

-- SERVICE CATEGORIES (providers pick one at profile creation)
CREATE TABLE IF NOT EXISTS service_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed categories (idempotent)
INSERT IGNORE INTO service_categories (name, description) VALUES
  ('Electrician', 'Wiring, repairs, installations'),
  ('Plumber', 'Pipes, leaks, sanitary fittings'),
  ('AC Technician', 'AC install, service, gas refill'),
  ('Carpenter', 'Furniture repair and custom woodwork'),
  ('Painter', 'Interior and exterior painting'),
  ('Cleaner', 'Home and office deep cleaning'),
  ('Pest Control', 'Insect and rodent control'),
  ('Appliance Repair', 'Fridge, washer, microwave repair'),
  ('Home Tutor', 'Academic home tuition'),
  ('Beautician', 'At-home beauty services'),
  ('CCTV / WiFi Installer', 'Camera and network installation'),
  ('Mason', 'Brickwork, plaster, tile fixing'),
  ('Auto Mechanic', 'At-doorstep car servicing'),
  ('Packers & Movers', 'Shifting and moving services'),
  ('Gardener', 'Lawn and garden maintenance');

-- CUSTOMER PROFILES (short form after role selection)
CREATE TABLE IF NOT EXISTS customer_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  address VARCHAR(255) NOT NULL,
  city VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_customer_user (user_id),
  CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SERVICE PROVIDER PROFILES (detailed form + admin verification workflow)
CREATE TABLE IF NOT EXISTS service_provider_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  category_id BIGINT NOT NULL,
  experience_years INT NOT NULL DEFAULT 0,
  skills TEXT NULL,                      -- comma separated, e.g. "Wiring,Fan install"
  bio VARCHAR(500) NULL,
  address VARCHAR(255) NOT NULL,
  city VARCHAR(80) NOT NULL,
  verification_status ENUM('NOT_SUBMITTED','PENDING_REVIEW','APPROVED','REJECTED') NOT NULL DEFAULT 'NOT_SUBMITTED',
  rejection_reason TEXT NULL,
  verified_at DATETIME NULL,
  reviewed_by_user_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_provider_user (user_id),
  KEY idx_provider_status (verification_status),
  CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_provider_category FOREIGN KEY (category_id) REFERENCES service_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PROVIDER DOCUMENTS (CNIC front / CNIC back / selfie — stored on disk, path in DB)
CREATE TABLE IF NOT EXISTS provider_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  doc_type ENUM('CNIC_FRONT','CNIC_BACK','SELFIE') NOT NULL,
  file_path VARCHAR(500) NOT NULL,       -- relative path under upload dir (never absolute)
  original_name VARCHAR(255) NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_provider_doc (user_id, doc_type),
  CONSTRAINT fk_doc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
