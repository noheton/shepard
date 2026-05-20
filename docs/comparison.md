---
layout: default
title: Upstream vs this fork — feature comparison
description: Side-by-side comparison of upstream shepard 5.2.0 and this fork's additional capabilities.
---

# Feature comparison: upstream vs this fork

**Legend:** ✓ shipped · 📐 designed · 🗓 planned · — not present

**Compatibility:** every `/shepard/api/…` path is wire-compatible with upstream 5.2.0.
All fork additions live under `/v2/…` or in the UI.

---

## Payload kinds

| Kind | Upstream | This fork | Status |
|---|---|---|---|
| Timeseries | ✓ | ✓ + anomaly detection, annotations, streaming export | ✓ |
| File bundle / single file | ✓ | ✓ | ✓ |
| Structured data (JSON) | ✓ | ✓ | ✓ |
| Video stream reference | ✓ | ✓ (bug-fixed) | ✓ |
| HDF5 containers | — | designed (A5 series) | 📐 |

---

## Core graph

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| Collections + DataObjects | ✓ | ✓ | ✓ |
| Semantic annotations | ✓ | ✓ + admin-configurable ontology preseed | ✓ |
| Provenance (PROV-O) | ✓ | ✓ + richer graph at `/v2/provenance/entity/{appId}` | ✓ |
| Collection snapshots | ✓ | ✓ | ✓ |
| DataObject templates | — | full CRUD, YAML import/export, server-side instantiation | ✓ |
| WATCH links (pin live containers to a collection) | — | `GET/POST/DELETE /v2/collections/{appId}/watched-containers` | ✓ |
| RO-Crate export | ✓ | ✓ + UI download button | ✓ |
| DataObject lifecycle status | — | status field + UI chip | ✓ |
| Collection Lineage Graph | — | interactive force graph on collection page | ✓ |
| DataObject Provenance Graph | — | interactive force graph on dataset page | ✓ |
| Per-kind reference counts on list | — | `timeseriesCount`, `fileCount`, `structuredDataCount` on `/v2/data-objects` list | ✓ |
| Collection bundling / project labels | — | — | 🗓 |
| Archive state (frozen collections) | — | — | 📐 |

---

## Timeseries extras

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| Ingest + query | ✓ | ✓ | ✓ |
| Anomaly detection | — | Z-score sliding window; `POST /v2/timeseries-references/{id}/detect-anomalies` | ✓ |
| Annotations (labeled time windows) | — | full CRUD at `/v2/timeseries-references/{id}/annotations` | ✓ |
| Per-container persisted channel view | — | `GET/PATCH /v2/timeseries-containers/{id}/chart-view` | ✓ |
| Storage stats | — | `GET /v2/timeseries-containers/{id}/stats` (point count + size) | ✓ |
| Streaming export (JSON / CSV / NDJSON) | — | server-side cursor; statement timeout → HTTP 504 | ✓ |
| Live window endpoint (boundary point injection) | — | designed | 📐 |

---

## UI

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| Web UI | ✓ | ✓ (Nuxt 3 + Vuetify 3, modernised) | ✓ |
| Personal landing page | static marketing page | collections digest — 6 most-recent collections + quick-create | ✓ |
| Inline timeseries chart | — | ECharts line chart; channel selection; live-mode; curated channel view | ✓ |
| Channel preview sparklines | — | per-row mini-chart on timeseries container page | ✓ |
| Collection Lineage Graph | — | drag-zoom force graph | ✓ |
| DataObject Provenance Graph | — | drag-zoom force graph | ✓ |
| Global search | basic | type-ahead collection search in header; sidebar data-object filter | ✓ |
| Templates browser (admin) | — | tabular list, create/edit/retire, YAML import | ✓ |
| Admin health dashboard | — | heap, uptime, HTTP metrics | ✓ |
| In-app user docs (`/help`) | — | full docs site served inline | ✓ |
| Dark mode | — | ✓ | ✓ |
| 401 auto-refresh | manual re-login | transparent token refresh + session-expiry warning snackbar | ✓ |
| Timeseries chart → uPlot (streaming performance) | — | ECharts today; uPlot migration designed | 📐 |
| Containerless UX (basic mode) | — | — | 🗓 |
| Gallery / card view | — | — | 🗓 |

---

## Identity + auth

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| OIDC / Keycloak | ✓ | ✓ | ✓ |
| User profile (avatar, ORCID, JupyterHub URL) | basic | ✓ | ✓ |
| Bootstrap token (admin seeding) | — | generated at first start | ✓ |
| Admin CLI (`shepard-admin`) | — | status, migrate, feature-toggle, semantic, unhide | ✓ |
| ORCID + eLib publication records | — | — | 🗓 |

---

## Publishing + integrations

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| DataCite PIDs | ✓ | ✓ | ✓ |
| ePIC PIDs | — | minter plugin | ✓ |
| Helmholtz Unhide publish | — | runtime-configurable; per-collection toggle | ✓ |
| ROR organisation identity | — | `GET /v2/instance/identity` + ROR preseed | ✓ |
| MCP endpoint (AI agent access) | — | designed | 📐 |
| Git-native data ingest | — | — | 🗓 |

---

## Quality + ops

| Feature | Upstream | This fork | Status |
|---|---|---|---|
| JaCoCo coverage gate (≥ 60% line + branch) | — | enforced in CI | ✓ |
| SpotBugs + CodeQL SAST | — | enforced in CI | ✓ |
| OWASP Dependency-Check SCA | — | weekly + on pom.xml touch | ✓ |
| Trivy container CVE scan | — | on every image push | ✓ |
| CycloneDX SBOM | — | attached to every GitHub release | ✓ |
| API-level integration test suite | — | pytest + httpx; 22 tests; daily CI drift check | ✓ |
| Deployment smoke test | — | `infrastructure/smoke-test.sh` | ✓ |

---

*Contributor-facing detail: [`aidocs/44-fork-vs-upstream-feature-matrix.md`](https://github.com/your-org/shepard/blob/main/aidocs/44-fork-vs-upstream-feature-matrix.md)*
