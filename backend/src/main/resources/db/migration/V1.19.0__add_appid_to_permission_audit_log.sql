-- APISIMP-PERM-AUDIT-LOG-APPID — swap the opaque BIGSERIAL row id for a stable UUID
-- that callers and log-correlation tools can use without coupling to Postgres internals.
-- The internal BIGSERIAL PK is preserved; only the JSON-wire field changes.
--
-- Operator runbook: safe to run while the application is live. ADD COLUMN with a
-- volatile DEFAULT (gen_random_uuid()) backfills every existing row atomically.
-- No data is lost. After deploying the new backend build, GET /v2/admin/permission-audit/log
-- returns appId instead of id for each entry.
--
-- Rollback:
--   DROP INDEX IF EXISTS perm_audit_app_id_idx;
--   ALTER TABLE permission_audit_log DROP COLUMN IF EXISTS app_id;
ALTER TABLE permission_audit_log
    ADD COLUMN IF NOT EXISTS app_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS perm_audit_app_id_idx
    ON permission_audit_log (app_id);
