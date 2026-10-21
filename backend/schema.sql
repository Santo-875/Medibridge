-- MediBridge Unified PostgreSQL Schema for Supabase
-- Can be run directly in the Supabase SQL editor or executed via migration

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(32) UNIQUE,
    password_hash VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

CREATE TABLE IF NOT EXISTS profiles (
    id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    avatar VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_profiles_owner ON profiles(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_updated ON profiles(updated_at);

CREATE TABLE IF NOT EXISTS care_links (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    caretaker_user_id VARCHAR(64) REFERENCES users(id) ON DELETE SET NULL,
    permission VARCHAR(32) NOT NULL DEFAULT 'view',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    invite_code VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_care_links_profile ON care_links(profile_id);
CREATE INDEX IF NOT EXISTS idx_care_links_caretaker ON care_links(caretaker_user_id);
CREATE INDEX IF NOT EXISTS idx_care_links_status ON care_links(status);

CREATE TABLE IF NOT EXISTS medications (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name VARCHAR(256) NOT NULL,
    dosage VARCHAR(128),
    strength VARCHAR(128),
    timing VARCHAR(128),
    food_rule VARCHAR(128),
    duration_days INT,
    source VARCHAR(64),
    rxcui VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_medications_profile ON medications(profile_id);
CREATE INDEX IF NOT EXISTS idx_medications_updated ON medications(updated_at);

CREATE TABLE IF NOT EXISTS dose_times (
    id VARCHAR(64) PRIMARY KEY,
    medication_id VARCHAR(64) NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    time VARCHAR(32) NOT NULL,
    slot VARCHAR(32) NOT NULL DEFAULT 'MORNING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_dose_times_med ON dose_times(medication_id);
CREATE INDEX IF NOT EXISTS idx_dose_times_profile ON dose_times(profile_id);

CREATE TABLE IF NOT EXISTS dose_logs (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    medication_id VARCHAR(64) NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    dose_time_id VARCHAR(64),
    scheduled_time VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    taken_at TIMESTAMPTZ,
    snooze_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_dose_logs_profile ON dose_logs(profile_id);
CREATE INDEX IF NOT EXISTS idx_dose_logs_med ON dose_logs(medication_id);
CREATE INDEX IF NOT EXISTS idx_dose_logs_updated ON dose_logs(updated_at);

CREATE TABLE IF NOT EXISTS prescriptions (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) REFERENCES profiles(id) ON DELETE SET NULL,
    file_path VARCHAR(512),
    ocr_raw_text TEXT,
    structured_json TEXT,
    prescribed_by VARCHAR(256),
    date VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_prescriptions_profile ON prescriptions(profile_id);

CREATE TABLE IF NOT EXISTS recordings (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) REFERENCES profiles(id) ON DELETE SET NULL,
    file_path VARCHAR(512),
    transcript_tamil TEXT,
    transcript_english TEXT,
    summary_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_recordings_profile ON recordings(profile_id);

CREATE TABLE IF NOT EXISTS safety_checks (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    medication_id VARCHAR(64),
    conflict_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    details TEXT NOT NULL,
    action_required TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_safety_checks_profile ON safety_checks(profile_id);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_notifications_profile ON notifications(profile_id);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token VARCHAR(512) NOT NULL,
    platform VARCHAR(32) DEFAULT 'android',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
