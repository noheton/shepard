// TS_CHART_VIEW1 — Uniqueness constraint on :TimeseriesContainerChartView.appId
//
// Runbook pointer: aidocs/34-upstream-upgrade-path.md §TS_CHART_VIEW1
//
// Additive + idempotent (CREATE CONSTRAINT IF NOT EXISTS). The label is
// new as of this commit — no pre-existing rows in any production graph,
// so no backfill needed.
//
// After this migration, TimeseriesContainerChartViewService.patch lazily
// creates one node per container on first PATCH (GenericDAO.createOrUpdate
// mints the UUID v7 appId); subsequent PATCHes mutate the same node in
// place. Service-layer uniqueness on containerAppId is enforced by the
// "find then update" flow rather than a Cypher constraint (containerAppId
// is not an entity primary key; the appId is).
//
// To roll back manually: DROP CONSTRAINT timeseries_container_chart_view_appId_unique IF EXISTS;
CREATE CONSTRAINT timeseries_container_chart_view_appId_unique IF NOT EXISTS
FOR (n:TimeseriesContainerChartView) REQUIRE n.appId IS UNIQUE;
