// V84 rollback — drop HdfReference indexes and constraint
//
// Safe to run even if V84 was not applied (IF EXISTS guards).

DROP INDEX hdf_reference_datasetPath_idx IF EXISTS;
DROP CONSTRAINT hdf_reference_appId_unique IF EXISTS;
