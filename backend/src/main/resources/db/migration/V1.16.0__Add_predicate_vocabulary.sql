-- V1.16.0 — Predicate vocabulary table (PR-5 enabler, task #156)
--
-- Mapping from shepard: predicate URIs to their authoritative substrate.
-- This is the routing table that lets the SHACL shape write path send
-- each predicate to the correct store (Neo4j graph properties/edges,
-- TimescaleDB channels, Postgres side-tables, Garage object storage).
--
-- Design: aidocs/semantics/95 §4 (named individuals + substrate split),
--         aidocs/semantics/98 §1.3 (predicate-key registry),
--         task brief 2026-05-22 (SHACL changeover PR-5).
--
-- Substrate vocabulary:
--   neo4j       — graph property or edge on :DataObject/:Collection/:Container/:Activity
--   timescaledb — TimescaleDB channel metadata or data point (PR-9 scope for 5-tuple migration)
--   postgres    — Postgres side-table (e.g. ledger anchor, audit log)
--   garage      — Garage / S3 object payload (referenced by IRI from Neo4j)
--
-- Cardinality:
--   one         — at most one value per subject (sh:maxCount 1 in shapes)
--   many        — multiple values allowed (default)
--
-- Idempotency: CREATE TABLE IF NOT EXISTS + INSERT … ON CONFLICT DO NOTHING
-- Rollback: DROP TABLE IF EXISTS predicate_vocabulary;
--
-- Operator runbook:
--   Re-run: psql -c "\i V1.16.0__Add_predicate_vocabulary.sql"
--   (safe — idempotent on conflict, table creation is IF NOT EXISTS)
--   Verify: SELECT substrate, count(*) FROM predicate_vocabulary GROUP BY substrate ORDER BY substrate;

CREATE TABLE IF NOT EXISTS predicate_vocabulary (
    predicate_uri   TEXT        PRIMARY KEY,
    substrate       TEXT        NOT NULL
        CHECK (substrate IN ('neo4j', 'timescaledb', 'postgres', 'garage')),
    cardinality     TEXT        NOT NULL DEFAULT 'many'
        CHECK (cardinality IN ('one', 'many')),
    writable        BOOLEAN     NOT NULL DEFAULT true,
    description     TEXT,
    shape_file      TEXT,           -- which .ttl file declared this predicate (informational)
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS predicate_vocab_substrate_idx
    ON predicate_vocabulary (substrate);

-- ─── Seed: core container metadata predicates (shepard-core-shapes.ttl) ────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#status',
   'neo4j', 'one', true,
   'Lifecycle status of a DataObject or Collection (DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED/NCR_OPEN/REJECTED).',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#storageBackend',
   'neo4j', 'one', true,
   'Physical storage adapter name for a Container (influxdb/mongodb/minio/postgres/hdf5/filesystem).',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#channelCount',
   'neo4j', 'one', true,
   'Number of timeseries channels in a TimeseriesContainer (metadata, not the channel data itself).',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#sampleRateHz',
   'neo4j', 'one', true,
   'Nominal sample rate (Hz) for a TimeseriesContainer.',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#fileCount',
   'neo4j', 'one', true,
   'Number of files in a FileContainer (metadata).',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#totalSizeBytes',
   'neo4j', 'one', true,
   'Total payload size in bytes for a FileContainer.',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#documentCount',
   'neo4j', 'one', true,
   'Number of documents in a StructuredDataContainer.',
   'shepard-core-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#schemaIri',
   'neo4j', 'one', true,
   'IRI of the JSON schema governing a StructuredDataContainer.',
   'shepard-core-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: NCR / mini-shape predicates (mini-shapes.ttl) ────────────────────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#ncrIdentifier',
   'neo4j', 'one', true,
   'NCR identifier (pattern NCR-XXXXX), per EN 9100 §8.7.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#findingText',
   'neo4j', 'one', true,
   'Human-readable non-conformance finding prose on an NCR.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#severity',
   'neo4j', 'one', true,
   'NCR severity (MINOR/MAJOR/CRITICAL).',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#rootCause',
   'neo4j', 'one', true,
   'Root cause analysis text on an NCR.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#correctiveAction',
   'neo4j', 'one', true,
   'Corrective action description on an NCR.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#ncrStatus',
   'neo4j', 'one', true,
   'NCR lifecycle status (OPEN/IN_PROGRESS/CLOSED/REJECTED).',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#raisedAgainst',
   'neo4j', 'many', true,
   'Edge from an NCR to the DataObject(s) it pertains to.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#approvalDocument',
   'garage', 'one', true,
   'IRI of the approval document payload in Garage (FileContainer-backed).',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#role',
   'neo4j', 'one', true,
   'Role / authority label on a SignOff activity.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#verifiedClaim',
   'neo4j', 'many', true,
   'Edge from a VerificationActivity to the claim IRIs it verified.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#newVerificationState',
   'neo4j', 'one', true,
   'F(AI)²R verification rung reached by a VerificationActivity.',
   'mini-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#verificationMethod',
   'neo4j', 'one', true,
   'Method used (manual-review/literature-citation/independent-replication/ai-cross-check/ledger-anchor).',
   'mini-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: ledger-anchor predicates (ledger-anchor-shapes.ttl) ──────────────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#hashSha256',
   'postgres', 'one', false,
   'SHA-256 digest of the anchored payload; stored in Postgres for transactional integrity.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#hashAlgorithm',
   'postgres', 'one', false,
   'Hash algorithm identifier (e.g. SHA-256) accompanying hashSha256.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#anchoredAt',
   'postgres', 'one', false,
   'Timestamp when the ledger anchor was committed.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#anchors',
   'neo4j', 'many', false,
   'Edge from a ledger anchor record to the DataObject/Container IRIs it covers.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#ledgerName',
   'postgres', 'one', false,
   'Name of the ledger (e.g. bloxberg, openTimestamps) the anchor was submitted to.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#ledgerTxId',
   'postgres', 'one', false,
   'Transaction / block identifier on the target ledger.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#retainUntil',
   'postgres', 'one', true,
   'ISO 8601 date until which the anchored artefact must be retained.',
   'ledger-anchor-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#independenceProof',
   'postgres', 'one', false,
   'Merkle proof or inclusion certificate from the ledger (binary blob or JSON).',
   'ledger-anchor-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: pipeline-shapes predicates (pipeline-shapes.ttl) ─────────────────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#hasTask',
   'neo4j', 'many', true,
   'Edge from a PipelineRun to its constituent Task DataObjects.',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#pipelineVersion',
   'neo4j', 'one', true,
   'Semantic version of the pipeline definition that produced this run.',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#taskIdentifier',
   'neo4j', 'one', true,
   'Unique short identifier for a task within a pipeline run.',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#taskStatus',
   'neo4j', 'one', true,
   'Pipeline task status (PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED).',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#containerImage',
   'neo4j', 'one', true,
   'OCI container image reference used to execute a pipeline task.',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#script',
   'neo4j', 'one', true,
   'Inline script or entry-point command for a pipeline task.',
   'pipeline-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#dependsOn',
   'neo4j', 'many', true,
   'DAG dependency edge between pipeline tasks (must complete before this task starts).',
   'pipeline-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: REP-shapes predicates (rep-shapes.ttl) ───────────────────────────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#repIdentifier',
   'neo4j', 'one', true,
   'Regulatory Evidence Package identifier.',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#hasLearningAssurancePlan',
   'neo4j', 'many', true,
   'Edge from a REP to its Learning Assurance Plan DataObject(s).',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#hasOperationalDesignDomain',
   'neo4j', 'many', true,
   'Edge from a REP to its Operational Design Domain DataObject(s).',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#hasDQR',
   'neo4j', 'many', true,
   'Edge from a REP to its Data Quality Requirement DataObject(s).',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#overallStatus',
   'neo4j', 'one', true,
   'Aggregated review status of a REP (DRAFT/IN_REVIEW/APPROVED/REJECTED).',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#easaObjective',
   'neo4j', 'one', true,
   'EASA CS-25 / AMC objective identifier this REP satisfies.',
   'rep-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#signOffActivity',
   'neo4j', 'many', true,
   'Edge from a REP to the SignOff Activity that approved it.',
   'rep-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: DQR-shapes predicates (dqr-shapes.ttl) ───────────────────────────

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#dqrIdentifier',
   'neo4j', 'one', true,
   'Data Quality Requirement identifier.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#requirementText',
   'neo4j', 'one', true,
   'Prose text of the DQR.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#validationMethod',
   'neo4j', 'one', true,
   'Method by which this DQR is validated (automated/manual/statistical).',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#validationResult',
   'neo4j', 'one', true,
   'Outcome of the most recent DQR validation run (PASS/FAIL/PENDING).',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#validatedAt',
   'neo4j', 'one', true,
   'Timestamp of the most recent DQR validation.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#validationActivity',
   'neo4j', 'many', true,
   'Edge from a DQR to the Activity that ran the validation.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#targetDataset',
   'neo4j', 'many', true,
   'Edge from a DQR to the DataObject / Collection it targets.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#trainingDataset',
   'neo4j', 'many', true,
   'Edge from a REP/DQR to the training-dataset DataObject(s) used.',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#testDataset',
   'neo4j', 'many', true,
   'Edge from a REP/DQR to the held-out test-dataset DataObject(s).',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#validationDataset',
   'neo4j', 'many', true,
   'Edge from a REP/DQR to the validation-dataset DataObject(s).',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#exportFormat',
   'neo4j', 'one', true,
   'Target export format for a dataset (CSV/HDF5/NETCDF/PARQUET).',
   'dqr-shapes.ttl'),

  ('http://semantics.dlr.de/shepard-upper#schemaIri',
   'neo4j', 'one', true,
   'IRI of the governing schema (also used by StructuredDataContainer).',
   'dqr-shapes.ttl')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Seed: V61 provenance predicates (V61__v15_prov_predicates.cypher) ───────
-- These are in the Neo4j :Resource store via n10s but their substrate
-- is still neo4j — the activity attributes land on :Activity nodes.

INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, writable, description, shape_file) VALUES
  ('http://semantics.dlr.de/shepard-upper#targetCollection',
   'neo4j', 'one', false,
   'Destination Collection scoped by an import AuthoringPass.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#filesUploaded',
   'neo4j', 'one', false,
   'Count of FileContainer payload uploads completed by an AuthoringPass.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#timeseriesImported',
   'neo4j', 'one', false,
   'Count of TimeseriesReferences materialised by an AuthoringPass.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#structuredPayloads',
   'neo4j', 'one', false,
   'Count of StructuredDataReference payloads imported by an AuthoringPass.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#batchSequence',
   'neo4j', 'one', false,
   'Monotonic per-(script,source) batch index; gaps indicate missed batches.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#throughputBytesPerSec',
   'neo4j', 'one', false,
   'Observed payload throughput in bytes per second during an AuthoringPass.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#retryCount',
   'neo4j', 'one', false,
   'HTTP retries observed by an AuthoringPass; non-zero = backpressure.',
   'V61__v15_prov_predicates.cypher'),

  ('http://semantics.dlr.de/shepard-upper#sourceInstance',
   'neo4j', 'one', false,
   'Cross-instance partition key for SPARQL UNIONs over migrated provenance.',
   'V61__v15_prov_predicates.cypher')

ON CONFLICT (predicate_uri) DO NOTHING;

-- ─── Verification probe ────────────────────────────────────────────────────
-- SELECT substrate, count(*) FROM predicate_vocabulary GROUP BY substrate ORDER BY substrate;
-- Expected first-run result:
--   garage      |  2
--   neo4j       | 41
--   postgres    |  8
-- (total 51; skew expected — neo4j is the primary graph substrate)
