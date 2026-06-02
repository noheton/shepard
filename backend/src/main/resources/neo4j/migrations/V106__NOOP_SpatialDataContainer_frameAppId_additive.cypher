// MFFD-SPATIAL-FRAME-HANDSHAKE — INTENTIONALLY EMPTY.
//
// SpatialDataContainer gains an optional `frameAppId` String property
// pointing at a CoordinateFrame node's appId (CST1 / aidocs/data/85).
// The PostGIS side of the substrate already carries
// `shepard_spatial.profile_container.coord_frame_app_id` (see the
// shipped Flyway migration V2.0.0__green_field_schema.sql in the
// spatiotemporal plugin). This Cypher NOOP records the analogous
// addition on the Neo4j side so a future graph query can traverse
// from a :SpatialDataContainer directly to the :CoordinateFrame node
// without having to round-trip through the PostGIS row.
//
// Neo4j is schema-less, so additive nullable properties need no DDL
// change. Existing :SpatialDataContainer nodes simply lack the
// property and Spring Data Neo4j OGM reads the absence as `null`.
// The wire representation omits the field when null
// (@JsonInclude(NON_NULL) on SpatialDataContainerIO) so the legacy
// /shepard/api/spatialDataContainers surface stays byte-identical
// to its pre-change shape.
//
// Operator runbook:
//   - No action required.
//   - To inspect containers bound to a frame:
//       MATCH (c:SpatialDataContainer) WHERE c.frameAppId IS NOT NULL
//       RETURN c.appId, c.name, c.frameAppId LIMIT 50;
//   - To bind a container to a frame at runtime (admin override):
//       MATCH (c:SpatialDataContainer {appId: $cId})
//       SET c.frameAppId = $frameAppId;
//
// Forward path: a real :ANCHORED_IN edge ships with SPATIAL-V6-006
// (graph-side mirror of the FK). That edge migration will arrive
// alongside the CST1 entities; until then `frameAppId` is the
// FK-by-convention.
//
// Companion: V97__NOOP_BasicContainer_status_additive.cypher
// (the established additive-property NOOP pattern).
//
// Rollback: V106_R__NOOP_SpatialDataContainer_frameAppId_additive.cypher
RETURN 1;
