-- Per-batch progress tracking for the InfluxDB -> TimescaleDB migration tool.
-- Written by the migration container, read by the backend HTTP endpoint.
CREATE TABLE migration_progress (
  container_id      bigint       NOT NULL PRIMARY KEY,
  rows_total        bigint       NOT NULL DEFAULT 0,
  rows_migrated     bigint       NOT NULL DEFAULT 0,
  rows_failed       bigint       NOT NULL DEFAULT 0,
  last_batch_index  integer      NOT NULL DEFAULT 0,
  status            varchar(16)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
  started_at        timestamp(6),
  last_update_at    timestamp(6) NOT NULL DEFAULT now(),
  errors            TEXT         NOT NULL DEFAULT ''
);

CREATE INDEX idx_migration_progress_status ON migration_progress (status);
