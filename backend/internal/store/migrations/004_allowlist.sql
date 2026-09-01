-- Allowlist: known-good numbers that should never be flagged as scams
-- These are numbers we own (SignalWire), trust (banks, government), or verify (family)

CREATE TABLE IF NOT EXISTS allowlist (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    number_hash TEXT    NOT NULL,
    tenant_id   TEXT    NOT NULL DEFAULT 'default',
    e164        TEXT,
    label       TEXT    NOT NULL DEFAULT '',
    source      TEXT    NOT NULL DEFAULT 'manual',
    metadata    TEXT,
    added_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(number_hash, tenant_id)
);

CREATE INDEX idx_allowlist_hash ON allowlist(number_hash);
CREATE INDEX idx_allowlist_tenant ON allowlist(tenant_id);
CREATE INDEX idx_allowlist_source ON allowlist(source);
