-- Tenant Isolation Migration
-- Adds tenant_id column to all tables for multi-tenant isolation

-- Add tenant_id to scam_numbers
ALTER TABLE scam_numbers ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- Add tenant_id to scam_prefixes
ALTER TABLE scam_prefixes ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- Add tenant_id to scam_reports
ALTER TABLE scam_reports ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- Add tenant_id to model_versions
ALTER TABLE model_versions ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- Add tenant_id to scrape_jobs
ALTER TABLE scrape_jobs ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';
