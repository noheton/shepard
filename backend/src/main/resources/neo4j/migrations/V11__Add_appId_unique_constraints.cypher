// L2a — Phase 1 of the Neo4j-ID migration (aidocs/25 §2).
// Adds a uniqueness-only constraint on the new `appId` property for every
// protected node-entity label. The property itself is set on the write side
// by GenericDAO#createOrUpdate via AppIdGenerator.next() (UUID v7).
//
// Additive shape:
//   - IF NOT EXISTS so re-runs are no-ops on dev stacks where it already ran.
//   - Uniqueness only — NO non-null assertion, because rows pre-dating L2a
//     keep appId = null until L2b's backfill writes a value to every row.
//     The uniqueness check ignores nulls in Neo4j 5, so the constraint is
//     valid against an unbackfilled graph.
//
// Read paths and the public API are unchanged in L2a; appId is internal.
//
// Constraints are intentionally per concrete @NodeEntity label rather than
// on intermediate base labels (BasicEntity, BasicContainer, VersionableEntity)
// so the constraint surface mirrors the OGM's class-per-label registry — it
// stays accurate even if a future entity gains/loses an inherited label.

// auth
CREATE CONSTRAINT appId_unique_User IF NOT EXISTS FOR (n:User) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_UserGroup IF NOT EXISTS FOR (n:UserGroup) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_ApiKey IF NOT EXISTS FOR (n:ApiKey) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_Permissions IF NOT EXISTS FOR (n:Permissions) REQUIRE n.appId IS UNIQUE;

// subscriptions
CREATE CONSTRAINT appId_unique_Subscription IF NOT EXISTS FOR (n:Subscription) REQUIRE n.appId IS UNIQUE;

// data containers and payloads
CREATE CONSTRAINT appId_unique_FileContainer IF NOT EXISTS FOR (n:FileContainer) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_ShepardFile IF NOT EXISTS FOR (n:ShepardFile) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_StructuredDataContainer IF NOT EXISTS FOR (n:StructuredDataContainer) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_StructuredData IF NOT EXISTS FOR (n:StructuredData) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_SpatialDataContainer IF NOT EXISTS FOR (n:SpatialDataContainer) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_TimeseriesContainer IF NOT EXISTS FOR (n:TimeseriesContainer) REQUIRE n.appId IS UNIQUE;
// `Timeseries` is the OGM label for ReferencedTimeseriesNodeEntity.
CREATE CONSTRAINT appId_unique_Timeseries IF NOT EXISTS FOR (n:Timeseries) REQUIRE n.appId IS UNIQUE;

// references
CREATE CONSTRAINT appId_unique_BasicReference IF NOT EXISTS FOR (n:BasicReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_FileReference IF NOT EXISTS FOR (n:FileReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_URIReference IF NOT EXISTS FOR (n:URIReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_StructuredDataReference IF NOT EXISTS FOR (n:StructuredDataReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_SpatialDataReference IF NOT EXISTS FOR (n:SpatialDataReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_TimeseriesReference IF NOT EXISTS FOR (n:TimeseriesReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_CollectionReference IF NOT EXISTS FOR (n:CollectionReference) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_DataObjectReference IF NOT EXISTS FOR (n:DataObjectReference) REQUIRE n.appId IS UNIQUE;

// collections / data objects / lab journal
CREATE CONSTRAINT appId_unique_Collection IF NOT EXISTS FOR (n:Collection) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_DataObject IF NOT EXISTS FOR (n:DataObject) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_LabJournalEntry IF NOT EXISTS FOR (n:LabJournalEntry) REQUIRE n.appId IS UNIQUE;

// semantic
CREATE CONSTRAINT appId_unique_SemanticRepository IF NOT EXISTS FOR (n:SemanticRepository) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_SemanticAnnotation IF NOT EXISTS FOR (n:SemanticAnnotation) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_AnnotatableTimeseries IF NOT EXISTS FOR (n:AnnotatableTimeseries) REQUIRE n.appId IS UNIQUE;

// versioning
CREATE CONSTRAINT appId_unique_VersionableEntity IF NOT EXISTS FOR (n:VersionableEntity) REQUIRE n.appId IS UNIQUE;
CREATE CONSTRAINT appId_unique_Version IF NOT EXISTS FOR (n:Version) REQUIRE n.appId IS UNIQUE;
