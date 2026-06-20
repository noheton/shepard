// ROLLBACK for V115 — drops :HdfConfig appId uniqueness constraint
DROP CONSTRAINT appId_unique_HdfConfig IF EXISTS;
