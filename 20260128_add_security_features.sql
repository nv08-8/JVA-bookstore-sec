-- Migration: Add password expiration and login attempt tracking
-- Created: 2026-01-28

-- Add columns for password expiration tracking
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_expires_at TIMESTAMP;

-- Add columns for failed login tracking
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

-- Add column to track password change requirement
ALTER TABLE users ADD COLUMN IF NOT EXISTS force_password_change BOOLEAN DEFAULT FALSE;

-- Create login_audit table for security logging
CREATE TABLE IF NOT EXISTS login_audit (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    username VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    login_status VARCHAR(50), -- 'success', 'failed', 'locked'
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_user ON login_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_username ON login_audit(username);
CREATE INDEX IF NOT EXISTS idx_login_audit_attempted_at ON login_audit(attempted_at);

-- Create password_history table to prevent password reuse
CREATE TABLE IF NOT EXISTS password_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    old_password_hash VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON password_history(user_id);

-- Create SSO providers table
CREATE TABLE IF NOT EXISTS oauth_providers (
    id SERIAL PRIMARY KEY,
    provider_name VARCHAR(50) UNIQUE NOT NULL, -- 'google', 'facebook', 'github', etc
    client_id VARCHAR(255) NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(500),
    scopes TEXT, -- comma-separated list
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create oauth_accounts table to link users with OAuth providers
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_name VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    profile_data JSONB,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    UNIQUE(provider_name, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user ON oauth_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_oauth_accounts_provider ON oauth_accounts(provider_name, provider_user_id);

-- Create security_settings table for global security configurations
CREATE TABLE IF NOT EXISTS security_settings (
    id SERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value VARCHAR(500),
    setting_type VARCHAR(50), -- 'string', 'integer', 'boolean'
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default security settings
INSERT INTO security_settings (setting_key, setting_value, setting_type, description) 
VALUES 
('password_expiry_days', '90', 'integer', 'Number of days before password expires'),
('password_min_length', '8', 'integer', 'Minimum password length'),
('password_require_uppercase', 'true', 'boolean', 'Password must contain uppercase letters'),
('password_require_lowercase', 'true', 'boolean', 'Password must contain lowercase letters'),
('password_require_numbers', 'true', 'boolean', 'Password must contain numbers'),
('password_require_special', 'true', 'boolean', 'Password must contain special characters'),
('max_failed_login_attempts', '5', 'integer', 'Maximum number of failed login attempts before lockout'),
('lockout_duration_minutes', '30', 'integer', 'Minutes to lock account after max failed attempts'),
('password_history_count', '5', 'integer', 'Number of previous passwords to prevent reuse')
ON CONFLICT (setting_key) DO NOTHING;

-- Update existing users to set password_expires_at for the first time
UPDATE users 
SET password_expires_at = password_changed_at + INTERVAL '90 days'
WHERE password_expires_at IS NULL;
