-- Fix tenant isolation: unique constraint must include tenant_id
-- Each tenant should be able to have their own records for the same number hash
-- This migration fixes the ON CONFLICT clause issue in Upsert

-- We need to recreate the table with the correct unique constraint
-- SQLite doesn't support ALTER TABLE DROP CONSTRAINT, so we recreate

CREATE TABLE IF NOT EXISTS scam_numbers_new (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    number_hash TEXT    NOT NULL,
    tenant_id   TEXT    NOT NULL DEFAULT 'default',
    source      TEXT    NOT NULL DEFAULT 'unknown',
    scam_type   TEXT,
    risk_score  REAL    NOT NULL DEFAULT 0.5,
    report_count INTEGER NOT NULL DEFAULT 0,
    first_seen  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP,
    UNIQUE(number_hash, tenant_id)
);

-- Copy data (this will deduplicate if same hash exists for multiple tenants, keeping latest)
INSERT OR IGNORE INTO scam_numbers_new 
SELECT * FROM scam_numbers;

-- Drop old table and rename new one
DROP TABLE IF EXISTS scam_numbers;
ALTER TABLE scam_numbers_new RENAME TO scam_numbers;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_scam_numbers_hash ON scam_numbers(number_hash);
CREATE INDEX IF NOT EXISTS idx_scam_numbers_risk ON scam_numbers(risk_score DESC);
CREATE INDEX IF NOT EXISTS idx_scam_numbers_source ON scam_numbers(source);
CREATE INDEX IF NOT EXISTS idx_scam_numbers_expires ON scam_numbers(expires_at);
CREATE INDEX IF NOT EXISTS idx_scam_numbers_tenant ON scam_numbers(tenant_id);
