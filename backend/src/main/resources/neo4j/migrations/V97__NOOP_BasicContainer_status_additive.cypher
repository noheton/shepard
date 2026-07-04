// #27-ARCHIVED-01 / #27-CONTAINER-STATUS-01 — INTENTIONALLY EMPTY.
//
// Containers (FileContainer, TimeseriesContainer, StructuredDataContainer,
// plus plugin-provided ones such as HdfContainer) gain an optional `status`
// String property on their shared :BasicContainer parent label. Valid values
// mirror the AbstractDataObject status enumeration:
//   DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED.
//
// Neo4j is schema-less, so additive nullable properties need no DDL change:
// existing container nodes simply lack the property and Spring Data Neo4j
// OGM reads the absence as `null`. The ArchiveStateGuard treats a null
// `status` as effectively READY (unblocked) — pre-feature containers behave
// exactly as before.
//
// The `status` field on the wire stays absent until set (via the new
// PATCH /v2/{collections|containers}/{appId}/publication-state endpoint)
// thanks to @JsonInclude(NON_NULL) on BasicContainerIO + AbstractDataObjectIO.
// This preserves byte-fidelity on the legacy /shepard/api/ surface for
// upstream v5.2.0 clients that have never seen this field.
//
// Operator runbook: no action required. To inspect containers that have
// been archived:
//   MATCH (c)
//   WHERE (c:FileContainer OR c:TimeseriesContainer OR c:StructuredDataContainer
//          OR c:HdfContainer)
//     AND c.status = 'ARCHIVED'
//   RETURN labels(c)[0] AS kind, c.appId, c.name LIMIT 50;
//
// To flip a container back to READY (e.g. operator override):
//   MATCH (c {appId: '...'}) SET c.status = 'READY';
//
// Companion to: Collection already inherits `status` from AbstractDataObject
// (no migration needed) — see V57__NOOP_AbstractDataObject_fair_fields.cypher
// for the analogous additive pattern.
//
// Rollback: V97_R__NOOP_BasicContainer_status_additive.cypher
RETURN 1;
