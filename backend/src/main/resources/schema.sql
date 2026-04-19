-- DLQ Revive — PostgreSQL Schema
-- Run this on first setup or via Flyway migration

CREATE TABLE IF NOT EXISTS redrive_log (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition INT NOT NULL,
    "offset" BIGINT NOT NULL,
    redriven_at TIMESTAMP NOT NULL DEFAULT NOW(),
    redriven_by VARCHAR(100) NOT NULL,
    UNIQUE(topic, partition, "offset")
);

CREATE TABLE IF NOT EXISTS transform_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    expression TEXT NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_trail (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    topic VARCHAR(255),
    message_count INT DEFAULT 0,
    "user" VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    session_id VARCHAR(255)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_redrive_log_lookup ON redrive_log(topic, partition, "offset");
CREATE INDEX IF NOT EXISTS idx_audit_trail_user ON audit_trail("user", timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_trail_topic ON audit_trail(topic, timestamp);
