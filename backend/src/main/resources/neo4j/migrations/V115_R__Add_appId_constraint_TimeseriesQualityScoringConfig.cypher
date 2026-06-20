// FTOGGLE-QS-1 rollback — drop the unique constraint on TimeseriesQualityScoringConfig.appId
// Safe to re-run: DROP CONSTRAINT … IF EXISTS is idempotent.
DROP CONSTRAINT TimeseriesQualityScoringConfig_appId_unique IF EXISTS;
