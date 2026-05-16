-- Migration V1.7.0: Change container_id from int to bigint
-- This migration was applied to the database but the file was missing from the codebase
-- Creating it now to satisfy Flyway validation

ALTER TABLE migration_tasks
ALTER COLUMN container_id TYPE bigint;