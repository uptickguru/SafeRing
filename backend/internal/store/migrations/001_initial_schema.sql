-- SafeRing Initial Schema
-- Zero PII: only SHA-256 hashes stored, never original phone numbers.

-- Known scam numbers (hashed)
CREATE TABLE IF NOT EXISTS scam_numbers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    number_hash TEXT    NOT NULL UNIQUE,
    source      TEXT    NOT NULL DEFAULT 'unknown',
    scam_type   TEXT,
    risk_score  REAL    NOT NULL DEFAULT 0.5,
    report_count INTEGER NOT NULL DEFAULT 0,
    first_seen  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP
);

CREATE INDEX idx_scam_numbers_hash ON scam_numbers(number_hash);
CREATE INDEX idx_scam_numbers_risk ON scam_numbers(risk_score DESC);
CREATE INDEX idx_scam_numbers_source ON scam_numbers(source);
CREATE INDEX idx_scam_numbers_expires ON scam_numbers(expires_at);

-- Scam prefixes (grouped area/central-office codes)
CREATE TABLE IF NOT EXISTS scam_prefixes (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    prefix         TEXT    NOT NULL UNIQUE,
    country_code   TEXT    NOT NULL DEFAULT '1',
    risk_score     REAL    NOT NULL DEFAULT 0.0,
    scam_type      TEXT,
    report_count   INTEGER NOT NULL DEFAULT 0,
    samples_hashed INTEGER NOT NULL DEFAULT 0,
    last_updated   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scam_prefixes_prefix ON scam_prefixes(prefix);
CREATE INDEX idx_scam_prefixes_risk ON scam_prefixes(risk_score DESC);

-- User-submitted scam reports (hash only, no PII)
CREATE TABLE IF NOT EXISTS scam_reports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    number_hash TEXT    NOT NULL,
    tag         TEXT,
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source      TEXT    NOT NULL DEFAULT 'user_report'
);

CREATE INDEX idx_scam_reports_hash ON scam_reports(number_hash);
CREATE INDEX idx_scam_reports_date ON scam_reports(reported_at DESC);

-- ML model version tracking
CREATE TABLE IF NOT EXISTS model_versions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    version         TEXT    NOT NULL,
    model_type      TEXT    NOT NULL CHECK(model_type IN ('number', 'sms')),
    file_path       TEXT,
    download_url    TEXT,
    sha256          TEXT,
    file_size_bytes INTEGER,
    accuracy        REAL,
    is_active       INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(version, model_type)
);

CREATE INDEX idx_model_versions_active ON model_versions(is_active) WHERE is_active = 1;

-- Scrape job tracking
CREATE TABLE IF NOT EXISTS scrape_jobs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    source      TEXT    NOT NULL,
    started_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    status      TEXT    NOT NULL DEFAULT 'running',
    numbers_added  INTEGER NOT NULL DEFAULT 0,
    numbers_skipped INTEGER NOT NULL DEFAULT 0,
    error_msg   TEXT
);

CREATE INDEX idx_scrape_jobs_source ON scrape_jobs(source);
CREATE INDEX idx_scrape_jobs_status ON scrape_jobs(status);
