// MFFD-AF-TRACK-MAPPING-2-CYPHER — cross-process Predecessor edge index.
//
// Cross-cuts: aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md (GAP-4)
//             aidocs/integrations/118-mffd-process-chain-mapping.md (design)
//
// What this migration does
// ------------------------
// 1. Adds a range index on the {@code transitionKind} property of the
//    {@code has_successor} relationship (Neo4j 5+).
// 2. Documents the edge-property contract used by the loader code path
//    (POST /v2/admin/mffd/process-chain-mapping in `MffdProcessChainMappingRest`,
//    and the operator runbook in
//    `docs/admin/runbooks/mffd-process-chain-mapping.md`).
//
// What this migration does NOT do
// -------------------------------
// * It does NOT materialise any cross-process edges by itself. The
//   mapping data lives in a YAML file authored by the MFFD domain
//   expert (flo) and is loaded via the REST endpoint / runbook. Putting
//   the data here would couple "schema versioning" (Flyway-style
//   migration) with "domain data drift" (Florian iterates the mapping
//   on Mondays) — those two pace layers should not share a file.
// * It does NOT add OGM constraints on {@code transitionKind} — see
//   "edge-property contract" below. The property is unconstrained at
//   the OGM level so this migration is safe to merge in either order
//   with AAA2 (`QM1b`), which uses the same property on the same edge
//   label for the rework/concession/normal/re-test distinction.
//
// Edge-property contract
// ----------------------
// On any {@code (a:DataObject)-[r:has_successor]->(b:DataObject)} edge,
// the property {@code r.transitionKind} is one of the four canonical
// strings:
//
//   normal       — default value of an untyped edge (treat absent = normal)
//   rework       — `b` is a rework of `a` after an NDT FAIL
//   re-test      — `b` is a follow-up re-test of the same artefact
//   concession   — `a` was accepted as-is per a documented concession
//
// QM1b (AAA2) is the authoritative cross-reference for the value set;
// this migration adopts the same vocabulary so the two PRs converge
// without a coordinated merge order.
//
// Operator runbook
// ----------------
// Run via:
//   cypher-shell -u neo4j -p <password> \
//     -f V105__Mffd_process_chain_mapping_edge_index.cypher
//
// Rollback:
//   V105_R__Mffd_process_chain_mapping_edge_index.cypher
//   (DROP INDEX rel_has_successor_transitionKind IF EXISTS)
//
// Verify:
//   SHOW INDEXES YIELD name, type, entityType
//     WHERE name = 'rel_has_successor_transitionKind';
//   → 1 row, type = RANGE, entityType = RELATIONSHIP
//
// aidocs/16 MFFD-AF-TRACK-MAPPING-2-CYPHER

CREATE INDEX rel_has_successor_transitionKind IF NOT EXISTS
FOR ()-[r:has_successor]-()
ON (r.transitionKind);
