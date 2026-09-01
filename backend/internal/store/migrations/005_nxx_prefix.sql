-- Add NXX prefix columns for cluster detection
-- NXX = first 6 digits (country code + area code + exchange)
-- This is NOT PII — same NXX covers ~10,000 numbers in a geographic area

-- Add nxx_prefix to scam_numbers for cluster queries
ALTER TABLE scam_numbers ADD COLUMN nxx_prefix TEXT DEFAULT '';

-- Add nxx_prefix to scam_reports for cluster queries
ALTER TABLE scam_reports ADD COLUMN nxx_prefix TEXT DEFAULT '';

-- Index for fast NXX-based cluster lookups
CREATE INDEX IF NOT EXISTS idx_scam_numbers_nxx ON scam_numbers(nxx_prefix);
CREATE INDEX IF NOT EXISTS idx_scam_reports_nxx ON scam_reports(nxx_prefix);

-- Composite index for cluster queries: same NXX + same scam_type + recent
CREATE INDEX IF NOT EXISTS idx_scam_numbers_nxx_type ON scam_numbers(nxx_prefix, scam_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_scam_reports_nxx_tag ON scam_reports(nxx_prefix, tag, reported_at);
