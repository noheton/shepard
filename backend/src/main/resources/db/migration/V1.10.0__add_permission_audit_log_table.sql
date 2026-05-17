-- F3 — Permission audit log for grants/revokes/updates.
-- Postgres is the right store here: simple indexed queries by entity or actor,
-- no graph traversal needed, and TimescaleDB is already the operational DB.
--
-- Operator runbook: this table is append-only and never mutated by application
-- code. Retention is left to the operator (e.g. partition pruning, pg_partman).
-- All columns except id, occurred_at, entity_app_id, action are nullable
-- (best-effort metadata — a log failure must never block a permissions write).
--
-- Rollback: DROP TABLE IF EXISTS permission_audit_log;
-- (No data-mutating side effects — safe to drop and re-create.)
CREATE TABLE IF NOT EXISTS permission_audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    entity_app_id  TEXT         NOT NULL,   -- the entity whose permissions changed
    entity_kind    TEXT,                    -- 'Collection', 'DataObject', etc. (nullable, best-effort)
    actor_username TEXT,                    -- who made the change (JWT sub / display name)
    action         TEXT         NOT NULL,   -- 'GRANT' | 'REVOKE' | 'UPDATE'
    detail_json    TEXT                     -- JSON blob of what changed (before/after roles, added/removed users)
);

CREATE INDEX IF NOT EXISTS perm_audit_entity_app_id_idx
    ON permission_audit_log (entity_app_id);

CREATE INDEX IF NOT EXISTS perm_audit_occurred_at_idx
    ON permission_audit_log (occurred_at DESC);
