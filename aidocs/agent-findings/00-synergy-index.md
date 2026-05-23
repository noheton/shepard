---
stage: fragment
last-stage-change: 2026-05-23
---

# 00 — Synergy index

This index lists synergy findings produced by the standing **synergy
agent** described in
`/root/.claude/projects/-opt-shepard/memory/feedback_synergy_agent_standing.md`.

A **synergy** is a combination of two or more Shepard elements
(payload kinds, plugins, ontologies, personas, substrates, external
integrations) whose joint value exceeds the sum of each in isolation.
Each row points to a single-file finding under
`aidocs/agent-findings/synergy-<date>-<slug>.md`. Per the standing
rule each file is ≤ 500 lines and carries: claim, elements,
non-obviousness rationale, concrete output (view recipe / endpoint /
mapping table), real-world use case, external evidence, effort
estimate, and risks.

Anti-fluff filter: every finding must (a) name 2+ concrete elements,
(b) cite a concrete output, (c) name a real-world researcher /
operator who benefits, and (d) cite at least two external sources.
Findings that would apply to any data platform — not specifically
Shepard's element set — are auto-rejected.

## 2026-05-23 batch

| ID | Title | Hook | Effort |
|---|---|---|---|
| S-01 | [HSDS HDF5 × AAS TimeSeriesData × sTC channel-as-individual](synergy-2026-05-23-channel-as-individual.md) | The 5-tuple channel descriptor becomes one named individual that exports three ways: HDF5 dataset path, AAS Submodel `TimeSeries`, OPC UA NodeId. The TS-appId migration is the unlock. | M |
| S-02 | [OpenLineage RunEvent × F(AI)²R × PROV-O — EASA evidence for free](synergy-2026-05-23-openlineage-fair2r.md) | Every Airflow / MLflow task already emits a typed RunEvent. Tag the `producer` URI as `AIActivity` or `HumanActivity` at receive time and the EASA Learning Assurance evidence pack falls out of the existing PROV-O store. | M |
| S-03 | [Confluence import × Wiki-writer × Snapshot — round-trip wiki bridge](synergy-2026-05-23-roundtrip-wiki.md) | A Confluence export becomes a Shepard snapshot baseline; the wiki-writer plugin re-renders snapshot deltas back to Confluence. The snapshot chain IS the wiki revision history. | M |
| S-04 | [Trace3D × Video × DataBinding — synchronized 3D-trace + camera-PiP](synergy-2026-05-23-trace3d-video-sync.md) | TresJS 3D-trace path indexed by timestamp + HLS video plugin = a single scrubber drives both. The DataBinding live-mode SSE multiplexer already does the timing. MFFD TCP thermal-trail is the demo. | M |
| S-05 | [PIDINST × SOSA/SSN × AAS Nameplate — one PID, three exports](synergy-2026-05-23-pidinst-three-exports.md) | A single `schema:instrument <pidinst-handle>` SemanticAnnotation projects to (a) SOSA `:Sensor`, (b) AAS Nameplate submodel, (c) PROV-O `prov:wasAttributedTo`. PIDINST becomes the calibration-traceability glue. | S |
| S-06 | [Snapshots × Garage S3 (no versioning) — Shepard absorbs the gap](synergy-2026-05-23-snapshots-garage-gap.md) | Garage refuses to ship S3 object versioning. Shepard's COW snapshot + `payload SHA-256` already provides bit-exact immutability without it. The "Garage gap" stops being a blocker and becomes a marketing point. | S |
| S-07 | [SHACL × MCP tools × ShapesValidateRest — one validator, two surfaces](synergy-2026-05-23-shacl-driven-mcp.md) | The same SHACL shape that drives a Vue form auto-publishes as a typed MCP tool. `ShapesValidateRest` is the single validator. An ontology PR ships both a UI form and an agent tool in the same commit — no code changes. | M |
| S-08 | [MCP × Permission audit log × F(AI)²R — AI accountability dashboard](synergy-2026-05-23-ai-accountability.md) | Cross-join `AdminPermissionAuditApi` events with `:AiActivity` nodes by actor + timestamp. The result is a per-agent activity ledger: what the AI changed, what permissions it used, what claims it asserted. EU AI Act Article 50 compliance falls out. | S |

## Synthesis hooks

Read the findings as a triangle:

- **S-01, S-04, S-05** are *view-recipe* synergies — they unlock new
  ways the existing graph can be rendered, queried, or exported.
- **S-02, S-03, S-06** are *integration-pair* synergies — the
  combination of an in-tree feature plus an external system that
  closes a gap neither would close alone.
- **S-07, S-08** are *cross-cutting* — they leverage Shepard's
  ontology + provenance discipline against the broader regulatory /
  agent-tooling landscape.

The strongest are S-02 (low effort, high regulatory value), S-07
(unlocks compounding plugin growth), and S-08 (the EU AI Act
deadline is August 2026 — see *2nd-order observation* in the
agent-report below).
