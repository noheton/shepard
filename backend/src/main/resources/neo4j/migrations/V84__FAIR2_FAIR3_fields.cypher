// V84__FAIR2_FAIR3_fields.cypher
//
// FAIR2 + FAIR3 additive fields on :DataObject (and subclasses).
//
// FAIR2: `createdByOrcid` — server-stamped at create time from User.orcid.
//   Nullable; absent on pre-FAIR2 entities. No data backfill needed.
//
// FAIR3: `embargoEndDate` — user-provided ISO-8601 date string (e.g. "2027-12-31")
//   after which the embargo lifts. Nullable; only meaningful when
//   accessRights=EMBARGOED. No data backfill needed.
//
// This migration creates an index on embargoEndDate to enable future range
// queries for embargo-lifting jobs (e.g. "find all DataObjects whose embargo
// expired before today").
//
// Idempotent: IF NOT EXISTS guard prevents duplicate index errors on re-run.
//
// Operator runbook:
//   - Run automatically on startup via MigrationsRunner.
//   - No data loss risk — purely additive property + index.
//   - Rollback: V84_R__FAIR2_FAIR3_fields.cypher drops the index.
//
// Tests: DataObjectServiceFair2Test (FAIR2 stamp), PublishServiceFair3Test
//        (FAIR3 enforcement) — both in de.dlr.shepard package.

CREATE INDEX data_object_embargo_end_date IF NOT EXISTS
FOR (n:DataObject) ON (n.embargoEndDate);
