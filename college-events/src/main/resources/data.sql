-- ============================================================
-- DATA MIGRATION: Fix NULL values for new columns added via ddl-auto=update
-- This runs on every startup but is idempotent (safe to run multiple times)
-- ============================================================

-- Fix generate_certificate: Set NULL rows to TRUE (default behavior)
-- This ensures all events created before the column was added still show certificates
UPDATE events SET generate_certificate = TRUE WHERE generate_certificate IS NULL;
