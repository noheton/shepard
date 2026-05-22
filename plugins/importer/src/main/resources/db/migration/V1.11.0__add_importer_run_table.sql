-- IMP1a / PR-2 — Postgres table backing the `shepard-plugin-importer`
-- asynchronous run lifecycle.
--
-- This is the **first concrete instance** of the generic JobService
-- design proposed in aidocs/platform/32-long-running-process-pattern.md §3.
-- Column names deliberately match the kernel proposed there so a future
-- `common/jobs/Job` entity can adopt this table by renaming the
-- backing class without touching SQL or wire shape. The importer-
-- specific columns (`source_kind`, `source_config`,
-- `target_collection_app_id`) live alongside the kernel; they don't
-- belong in the generic surface but stay co-located until the
-- graduation pass.
--
-- Idempotent (`IF NOT EXISTS`) and fail-fast (any error aborts Flyway
-- which aborts startup — the CLAUDE.md migration rule). Operator
-- runbook: see plugins/importer/docs/install.md.

CREATE TABLE IF NOT EXISTS importer_run (
  -- UUID v7 minted at the service layer (not Hibernate-generated)
  -- so the caller knows the id at submit time before the row commits.
  id                       uuid          NOT NULL PRIMARY KEY,

  -- Source-adapter discriminator (Hibernate EnumType.STRING).
  -- `text` so adding a new kind is a no-DDL change. Closed enum on
  -- the application side; checked by ImporterSourceKind.valueOf(...).
  source_kind              text          NOT NULL,

  -- Submitting principal — sub claim or API-key id. The permission
  -- boundary: only this principal can GET/DELETE the row (admins
  -- post-A0 can list all).
  principal                varchar(255)  NOT NULL,

  -- Target Collection appId on the local (this) instance.
  -- Nullable for adapters with no single target (e.g. validate-only);
  -- enforced non-null at the service layer for actual imports.
  target_collection_app_id varchar(64),

  -- JobService state machine — PENDING / RUNNING / SUCCEEDED /
  -- FAILED / CANCELLED. CHECK constraint mirrors the
  -- ImporterRunStatus enum closed set; the constraint is a
  -- defence-in-depth belt-and-braces in case a misbehaving caller
  -- bypasses Hibernate.
  status                   text          NOT NULL DEFAULT 'PENDING'
                                         CHECK (status IN
                                           ('PENDING','RUNNING',
                                            'SUCCEEDED','FAILED',
                                            'CANCELLED')),

  -- Cooperative cancellation flag (per JobService §5).
  cancel_requested         boolean       NOT NULL DEFAULT false,

  -- Lifecycle timestamps. `created_at` is set at submit time;
  -- `started_at` when the scheduler claims the row; `finished_at`
  -- when status moves to a terminal value; `last_progress_at`
  -- updated on every adapter heartbeat (drives the stale-job reaper).
  created_at               timestamptz   NOT NULL DEFAULT now(),
  started_at               timestamptz,
  last_progress_at         timestamptz,
  finished_at              timestamptz,

  -- Kind-specific progress count; nullable when the adapter cannot
  -- estimate (unbounded streams).
  progress_total           bigint,
  progress_done            bigint        NOT NULL DEFAULT 0,
  progress_message         text,

  -- Error reporting on FAILED. `error_class` carries a stable code
  -- (`JOB_STALLED`, `JOB_CANCELLED`) or the internal exception
  -- class name; `error_message` is the human-readable, redacted
  -- detail.
  error_class              varchar(255),
  error_message            text,

  -- On success: the target Collection's appId (for the importer) or
  -- a presigned S3 URL (P12) for adapters that produce a blob.
  result_url               text,

  -- Kind-specific result detail (counts, statistics, etc.). jsonb
  -- so it can be queried with `result_metadata->>'collectionAppId'`
  -- by ops tooling.
  result_metadata          jsonb,

  -- The original request body, with sensitive fields redacted by
  -- the service layer before insert. jsonb for the same reason.
  request_payload          jsonb,

  -- Adapter credentials. PR-2 stores plaintext as a stub; PR-3
  -- ships the AES-GCM cipher keyed off $SHEPARD_INSTANCE_SECRET.
  -- The column is text-shaped (jsonb) for forward-compat with the
  -- envelope shape `{ciphertext, iv, alg}` PR-3 will write.
  source_config            jsonb
);

-- Index for the "my jobs by status" listing endpoint (PR-4).
CREATE INDEX IF NOT EXISTS idx_importer_run_principal_status
  ON importer_run (principal, status);

-- Index for the GC / reaper scan: "what's still RUNNING and is
-- last_progress_at older than stale-after?" and "what's terminal
-- and older than retention-days?"
CREATE INDEX IF NOT EXISTS idx_importer_run_status_progress
  ON importer_run (status, last_progress_at)
  WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_importer_run_status_finished
  ON importer_run (status, finished_at)
  WHERE status IN ('SUCCEEDED','FAILED','CANCELLED');

-- Index for the per-Collection "show me imports targeting this
-- collection" lookup (used by frontend in PR-6 to show import
-- history on a collection's detail panel).
CREATE INDEX IF NOT EXISTS idx_importer_run_target_collection
  ON importer_run (target_collection_app_id)
  WHERE target_collection_app_id IS NOT NULL;
