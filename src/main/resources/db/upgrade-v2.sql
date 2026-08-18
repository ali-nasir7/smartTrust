-- ============================================================================
-- SMARTTRUST — UPGRADE v1 (auth-only) -> v2 (email OTP + provider verification)
-- Run this ONLY if you already ran the OLD schema-auth.sql (v1).
-- For a FRESH database, run schema-auth.sql instead (it already contains v2).
-- Run in MySQL Workbench / DBeaver while the backend is stopped.
-- ============================================================================

USE smarttrust;

-- 1) users: role becomes optional (chosen AFTER email verification) + full_name
ALTER TABLE users
  MODIFY COLUMN role ENUM('CUSTOMER','SERVICE_PROVIDER','ADMIN') NULL DEFAULT NULL,
  ADD COLUMN full_name VARCHAR(120) NULL AFTER email_verified;

-- 2) auth_otp_codes: email delivery target + supersede support
ALTER TABLE auth_otp_codes
  ADD COLUMN email VARCHAR(190) NULL AFTER phone,
  ADD COLUMN superseded_at DATETIME NULL AFTER verified,
  ADD INDEX idx_otp_email_purpose (email, purpose, created_at);

-- 3) service categories + seed
CREATE TABLE IF NOT EXISTS service_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- 4) customer profiles (short form)
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

-- 5) service provider profiles (detailed form + verification workflow)
CREATE TABLE IF NOT EXISTS service_provider_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  category_id BIGINT NOT NULL,
  experience_years INT NOT NULL DEFAULT 0,
  skills TEXT NULL,
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

-- 6) provider documents (CNIC front / back / selfie)
CREATE TABLE IF NOT EXISTS provider_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  doc_type ENUM('CNIC_FRONT','CNIC_BACK','SELFIE') NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  original_name VARCHAR(255) NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_provider_doc (user_id, doc_type),
  CONSTRAINT fk_doc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Done. Existing users keep working: they already have a role set,
-- their status/flags are untouched.
