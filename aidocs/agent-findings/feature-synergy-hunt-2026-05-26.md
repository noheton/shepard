---
title: Feature & Synergy Hunt — latent items surfaced 2026-05-26
stage: deployed
last-stage-change: 2026-05-26
audience: contributors
ssot-of: feature-synergy-hunt-2026-05-26
---

# Feature & Synergy Hunt — 2026-05-26

Scan of `aidocs/platform/`, `aidocs/integrations/`, `aidocs/data/`,
`aidocs/semantics/`, `aidocs/agent-findings/`, and `aidocs/strategy/`
for features designed but not yet tracked in `aidocs/16-dispatcher-backlog.md`.

**Target:** 12–18 quality issues. **Filed:** 16.

---

## What I found

The design corpus is far ahead of the backlog. Eight synergy docs (`S-01`
through `S-08`), two plugin design docs (`aidocs/96`, `aidocs/97`), the
CITE/CT wave (`aidocs/data/107 §10`), and three platform docs (`TH1`,
`URI1`, `J2a/J2b`) all contained concrete, implementable ideas that had
never been filed as GitHub issues or backlog rows.

The dedup filter (grep over ~600 existing backlog IDs) caught three
apparent duplicates:
- `CITE-EMBARGO-FIELD` overlaps `FAIR3` (which already covers
  `accessRights` enum + `embargoEndDate`) — skipped.
- `S-01` (channel-as-individual) is substantially absorbed by the
  TS-appId migration (`aidocs/87`) + `ID-MIG1`/`ID-MIG2` rows — skipped.
- `S-02` (OpenLineage × F(AI)²R) is explicitly mapped via the `PROMPT1`
  backlog row — skipped.

---

## Issues filed (16)

| # | GitHub Issue | ID | Size | Source |
|---|-------------|-----|------|--------|
| 1 | [#1518](https://github.com/noheton/shepard/issues/1518) | TH1 | M | `aidocs/data/88` — File Thumbnail SPI |
| 2 | [#1519](https://github.com/noheton/shepard/issues/1519) | URI1 | M | `aidocs/platform/91` — HTTPS appId PID redirect |
| 3 | [#1520](https://github.com/noheton/shepard/issues/1520) | J2a | S | `aidocs/integrations/81` — JupyterHub OIDC token injection |
| 4 | [#1521](https://github.com/noheton/shepard/issues/1521) | J2b | S | `aidocs/integrations/81` — shepard-py SDK pre-auth |
| 5 | [#1522](https://github.com/noheton/shepard/issues/1522) | CITE-FEDERATION-PROBE | S | `aidocs/data/107 §10` — scope call with CITE-DIGITAL TL |
| 6 | [#1523](https://github.com/noheton/shepard/issues/1523) | CITE-TRIGGER-PROVENANCE | S | `aidocs/data/107 §10` — test-event trigger as prov:atTime |
| 7 | [#1524](https://github.com/noheton/shepard/issues/1524) | CT-DATOSX-INGEST | M | `aidocs/data/107 §10` — datos|x → HDF5 + File payloads |
| 8 | [#1525](https://github.com/noheton/shepard/issues/1525) | CT-SLICE-VIEWER | M | `aidocs/data/107 §10` — CtSliceStackShape VIEW_RECIPE + Vue panel |
| 9 | [#1527](https://github.com/noheton/shepard/issues/1527) | CT-HSDS-SCALE-AUDIT | M | `aidocs/data/107 §10` — HSDS vs. direct HDF5 at BT volume scale |
| 10 | [#1528](https://github.com/noheton/shepard/issues/1528) | S-03 | M | Synergy: round-trip wiki bridge (Confluence × wiki-writer × snapshots) |
| 11 | [#1529](https://github.com/noheton/shepard/issues/1529) | S-04 | M | Synergy: Trace3D × Video synchronized VIEW_RECIPE |
| 12 | [#1530](https://github.com/noheton/shepard/issues/1530) | S-05 | M | Synergy: PIDINST × SOSA/SSN × AAS Nameplate three-export |
| 13 | [#1531](https://github.com/noheton/shepard/issues/1531) | S-07 | M | Synergy: SHACL-to-MCP bridge — shapes auto-publish as MCP tools |
| 14 | [#1532](https://github.com/noheton/shepard/issues/1532) | S-08 | M | Synergy: AI accountability dashboard cross-join (EU AI Act Art. 50) |
| 15 | [#1533](https://github.com/noheton/shepard/issues/1533) | METRO-SCAFFOLD | M | `aidocs/96` — shepard-plugin-metrology module scaffold |
| 16 | [#1534](https://github.com/noheton/shepard/issues/1534) | PIPE-SCAFFOLD | M | `aidocs/97` — shepard-plugin-pipelines DAG manifest skeleton |

---

## Not filed — too large (L)

These items surfaced during the scan but exceed the XS/S/M size gate for
automated issue filing. They belong in the findings doc for human triage.

### Full shepard-plugin-metrology implementation
Source: `aidocs/integrations/96-metrology-spatial-analyzer.md`

Full implementation of `MetrologyProject` (Spatial Analyzer `.xit64` files,
Garage-backed) and `MetrologyStream` (Leica AT 901 LR 6-DoF traces,
TimescaleDB-backed). CHAMEO/m4i/AAS GeometryAndKinematics bindings.
The IREC demonstrator (89.30% TCP deviation reduction, ±0.3 mm aerospace
tolerance, MFFD double-curvature mould) is the acceptance test.

**Gate:** M1-VIEWS-AS-SHAPES-WAVE must ship first.
**Scaffold (M):** filed as METRO-SCAFFOLD (#1533).

### Full shepard-plugin-pipelines implementation
Source: `aidocs/97-shepard-pipelines.md`

Full DAG executor sidecar: `PipelineManifest` YAML, multi-worker fan-out,
`POST /v2/pipelines/run` + status polling, `PipelineCaptureFilter` for
PROV-O activity capture per step. Replaces REBAR for in-Shepard workflow
automation.

**Scaffold (M):** filed as PIPE-SCAFFOLD (#1534).

### CT-AI-DEFECT-CLASS-V3
Source: `aidocs/data/107 §10`

Third-generation AI defect classifier for µCT volumes: foundation-model
fine-tuning on ZLP BT scan library, few-shot learning from operator-confirmed
defect annotations. Requires CT-SLICE-VIEWER (#1525) + at minimum 50 labelled
scan volumes as training data.

### CT-VOLUME-RENDERER-V3
Source: `aidocs/data/107 §10`

GPU-accelerated volume renderer (WebGL 2 ray-marching or WebGPU) for
full-resolution µCT volumes in the browser. Transfer function editor,
multi-resolution LOD pyramid. Depends on CT-SLICE-VIEWER (#1525) and the
HSDS scale audit (CT-HSDS-SCALE-AUDIT, #1527).

---

## Not filed — positioning narrative (S-06)

**S-06** (`aidocs/agent-findings/synergy-2026-05-23-snapshots-garage-gap.md`):
Shepard's copy-on-write snapshot + SHA-256 compensates for Garage lacking
native S3 versioning. This is a **competitive positioning asset** (Shepard
provides immutable data lineage even on object stores without versioning),
not a feature to implement. Belongs in `aidocs/42-vision.md` "Where it's
going" rather than a GitHub issue.

---

## Not filed — dedup cuts

| Item | Reason |
|------|--------|
| CITE-EMBARGO-FIELD | Overlaps `FAIR3` (`accessRights` + `embargoEndDate` already tracked) |
| S-01 (channel-as-individual) | Substantially absorbed by `aidocs/87` TS-appId migration + `ID-MIG1`/`ID-MIG2` backlog rows |
| S-02 (OpenLineage × F(AI)²R) | Explicitly mapped by `PROMPT1` in `aidocs/16` |

---

## What surprised me

- **The synergy docs are the richest vein.** All 8 synergy docs (`aidocs/agent-findings/synergy-2026-05-23-*.md`) were written as a single batch but never fed back into the backlog. Five of eight contained clean, unfiled, implementable features (S-03, S-04, S-05, S-07, S-08).

- **S-07 (SHACL-to-MCP bridge) is near-zero implementation cost.** The MCP plugin already calls `GET /v2/shapes`; extending `tools/list` to enumerate shapes is 2–3 days of work and provides immediate, high-value agent capability. It's the highest ROI item in this batch.

- **S-08 (AI accountability dashboard) has a hard deadline.** EU AI Act Article 50 requires machine-readable AI activity marking by August 2026. Shepard has all three required substrates (F3 audit log + F(AI)²R + MCP call log) already shipped or designed. The cross-join is the missing piece, and it's M-sized. The deadline means this should rank above other M items.

- **TH1, URI1, J2a, J2b were fully designed but never backlogged.** All four have complete design docs with endpoint shapes, CDI interfaces, and size estimates. They were never filed as issues — not deferred, just overlooked. Classic design-corpus drift.

- **aidocs/data/107 §10 is a ready-made sprint backlog.** The 7 rows in that section were written as explicit backlog proposals with IDs, sizes, and gate conditions. Whoever wrote §10 intended them to become issues; they just weren't filed.

- **Metrology and pipelines plugins are orphaned L items with actionable M scaffolds.** Both `aidocs/96` and `aidocs/97` are complete feature-defined design docs that have never touched the backlog. Filing scaffold-only issues (METRO-SCAFFOLD, PIPE-SCAFFOLD) unblocks contributors without committing to the full L-sized implementation scope.

---

## Backlog rows to file in aidocs/16

Per `feedback_github_pm_policies.md`, the following rows should be added to
`aidocs/16-dispatcher-backlog.md` in the next maintenance pass. Issue numbers
are the GitHub references filed above.

```
| TH1       | M | File Thumbnail SPI — CDI ThumbnailProvider; built-in raster + text    | #1518 |
| URI1      | M | HTTPS appId PID redirect GET /id/{type}/{appId} → 303               | #1519 |
| J2a       | S | JupyterHub OIDC kernel token injection (SHEPARD_ACCESS_TOKEN)        | #1520 |
| J2b       | S | shepard-py SDK pre-auth via SHEPARD_ACCESS_TOKEN env var             | #1521 |
| CITE-FEDERATION-PROBE | S | Scope call with CITE-DIGITAL TL                       | #1522 |
| CITE-TRIGGER-PROVENANCE | S | Test-event trigger → prov:Activity on DataObject    | #1523 |
| CT-DATOSX-INGEST | M | datos|x → HDF5 + File payloads sidecar ingest script          | #1524 |
| CT-SLICE-VIEWER  | M | CtSliceStackShape VIEW_RECIPE + Vue CtSlicePanel               | #1525 |
| CT-HSDS-SCALE-AUDIT | M | HSDS vs. direct HDF5 at ZLP BT volume scale                 | #1527 |
| S-03-WIKI-BRIDGE | M | Round-trip wiki: Confluence import × wiki-writer × snapshots  | #1528 |
| S-04-TRACE3D-VIDEO | M | Trace3D × Video synchronized VIEW_RECIPE w/ unified playhead | #1529 |
| S-05-PIDINST | M | PIDINST × SOSA/SSN × AAS Nameplate three-export synergy           | #1530 |
| S-07-SHACL-MCP | M | SHACL shapes auto-publish as typed MCP tools/list entries       | #1531 |
| S-08-AI-LEDGER | M | AI accountability ledger cross-join + EU AI Act Art. 50 export  | #1532 |
| METRO-SCAFFOLD | M | shepard-plugin-metrology Maven module scaffold                  | #1533 |
| PIPE-SCAFFOLD  | M | shepard-plugin-pipelines DAG manifest + SPI scaffold            | #1534 |
```

---

## Word count: ~950
