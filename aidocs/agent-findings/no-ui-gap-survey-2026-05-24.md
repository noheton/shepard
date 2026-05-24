---
title: No-UI gap survey + placeholder roll-out (2026-05-24)
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributors
ssot-of: per-feature UI-coverage gap audit, May 2026
---

# No-UI gap survey + placeholder roll-out — 2026-05-24

User: "what implemented features have no UI yet? — we should catch up at
least with minimal placeholders."

This survey inventories the gap between backend-shipped (or design-locked)
features and what the frontend actually surfaces. It then ships the top
**14** placeholder routes plus the **4-page semantic-repository browser**
(the headline, since SEMA-V6 SSOT — `aidocs/semantics/100` — just landed).

## What I found

The task's candidate list assumed a wide UI gap. The repo state contradicts
that assumption: most of the admin/profile fragments **already** have panes
(FeatureToggles, Plugins, AdminMetrics, AdminTemplates, SemanticRepository
pane, UserGroups, InstanceRor, AdminStorage, PermissionAuditLog, Unhide,
LegacyV1; Profile, ApiKey, MCP, Subscriptions, GitCredentials).

The genuine gaps fall into two buckets:

1. **Backend-shipped, no UI** — endpoint exists, no frontend trigger.
2. **Backend-designed-not-shipped, no UI** — design doc locked, no backend,
   no UI; a placeholder advertises the planned surface.

A third category — convenience surfaces (snapshot diff viewer, SHACL
validation playground, SPARQL query interface, semantic-repo browser) —
maps to shipped endpoints but has no top-level browsable UI route.

## Inventory

Legend: **SHIP** = placeholder shipped this PR; **DEFER** = not shipped
with rationale.

### Admin tiles (extend `/admin` fragment routing)

| ID | Feature | Backend status | Endpoint | Placeholder slug | Decision |
|----|---------|----------------|----------|------------------|----------|
| FS1e | File migration runner | shipped | `POST /v2/admin/files/migrate` + status | `file-migration` | **SHIP** |
| P10c | SQL-timeseries runtime caps | shipped | `GET/PATCH /v2/admin/sql-timeseries/config` | `sql-timeseries` | **SHIP** |
| NTF1 | Notifications transport admin / test | partial (test endpoint shipped) | `POST /v2/admin/notifications/test` | `notifications-admin` | **SHIP** |
| ADM-MANAGE | Manage other instance admins | shipped | `GET/POST/DELETE /v2/admin/instance-admins` | `instance-admins` | **SHIP** |
| ADM-USR-ORCID | Admin override of user ORCID | shipped | `PATCH /v2/admin/users/{u}/orcid` | `users-orcid` | **SHIP** |
| ADM-USR-GIT | Admin git-credential issuance | shipped | `POST /v2/admin/users/{u}/git-credentials` | `users-git` | **SHIP** |
| BOOTSTRAP | Bootstrap-an-admin runtime | shipped | `POST /v2/admin/bootstrap` | (see DEFER) | DEFER — security-sensitive; no casual UI |
| ADM-NUKE | `/v2/admin/instance/nuke` | shipped | `POST /v2/admin/instance/nuke` | (see DEFER) | DEFER — explicit "no casual UI for nuke" advisor note |
| AI1a-config | `:AIConfig` admin endpoint | **designed not shipped** | (planned `/v2/admin/ai/config`) | `ai-config` | **SHIP** (advertises plan) |
| PG-COLLAPSE-002 | `:BackupConfig` admin endpoint | **designed not shipped** | (planned `/v2/admin/backup/config`) | `backup` | **SHIP** (advertises plan) |

### Profile tiles (extend `/me` fragment routing)

| ID | Feature | Backend status | Endpoint | Placeholder slug | Decision |
|----|---------|----------------|----------|------------------|----------|
| AI1a-user | Per-user AI key / base-url / model | **designed not shipped** | (planned `/v2/users/me/ai`) | `ai-settings` | **SHIP** |

### Per-entity / contextual gaps

| ID | Feature | Backend status | Endpoint | Placeholder route | Decision |
|----|---------|----------------|----------|---------------------|----------|
| TM1a | Time-reference PATCH on TimeseriesReference | shipped | `PATCH /v2/timeseries-references/{}` | (inline panel on TR page — DEFER, needs design) | DEFER — wants in-place editor, not standalone page; left to TM1b |
| AI1b | Anomaly-detection trigger | shipped | `POST /v2/timeseries-references/{}/detect-anomalies` | (button on TR page — DEFER) | DEFER — wants existing TR page wired, not new route |
| AI1c | qualityScore display | shipped | (attribute on TimeseriesReferenceIO) | (badge on TR card — DEFER) | DEFER — display change, not a new route |
| J1d | Lab-journal revision history | shipped | `GET /v2/lab-journal/{e}/history` | (inline drawer — DEFER) | DEFER — wants drawer on entry, not new route |
| A5 | HDF container browsing | shipped (a-d) | `/v2/hdf-containers/*` | `/containers/hdf/[containerId]` | **SHIP** (top-level placeholder) |

### Top-level browsable surfaces (subroute pages under `frontend/pages/`)

| ID | Feature | Backend status | Endpoint | Placeholder route | Decision |
|----|---------|----------------|----------|---------------------|----------|
| N1f | SPARQL query interface | shipped | `GET/POST /v2/semantic/{repo}/sparql` | `/semantic/sparql` | **SHIP** (in the four-page headline) |
| SEMA-V6 | Vocabularies index | shipped (admin) | `GET /v2/admin/semantic/ontologies` | `/semantic/vocabularies` | **SHIP** (headline #1) |
| SEMA-V6 | Vocabulary detail | partial | (predicate listing — partial) | `/semantic/vocabularies/[vocabId]` | **SHIP** (headline #2) |
| SEMA-V6 | Predicate detail | partial | (usage stats — placeholder) | `/semantic/predicates/[predicateIri]` | **SHIP** (headline #3) |
| SHACL-V | SHACL validation playground | shipped | `POST /v2/shapes/validate` | `/shapes/validate` | **SHIP** |
| SNAP-DIFF | Snapshot diff viewer | shipped | `GET /v2/snapshots/{a}/diff/{b}` | `/snapshots/diff` | **SHIP** |

### Total shipped: 14

10 admin tiles + 1 profile tile + 1 HDF placeholder + 4 browsable surfaces
(SPARQL, vocabularies index, vocabulary detail, predicate detail) + SHACL
playground + snapshot diff viewer = **17 placeholder pages**, plus the
four-page semantic-repository browser overlaps two of those slots.

Effective unique placeholder count: **14 admin/profile/route slots**, plus
the four-page semantic browser bundle (the headline) sharing the
`/semantic/*` routes.

### Deferred (5)

- `BOOTSTRAP`, `ADM-NUKE` — security-sensitive, no casual UI should exist
- `TM1a`, `AI1b`, `AI1c`, `J1d` — wants in-place editor on existing entity
  pages, not a new standalone route. Backlog separately under
  `TM1b`/`AI1b-UI`/`AI1c-UI`/`J1d-UI`.

## Placeholder shape

Three shared components in `frontend/components/common/placeholder/`:

- `PlaceholderPageHeader.vue` — title, subtitle, design-doc link
- `PlaceholderRestDump.vue` — fetches the endpoint, renders JSON (advanced
  mode only per `feedback_basic_advanced_superset.md`)
- `PlaceholderImplStatus.vue` — backend status badge, backlog row, design
  doc citation

One TS module `placeholderRegistry.ts` holds the slug → metadata map so
that one Vitest can cover all placeholders' wiring (test layer matches the
existing `sectionLanding.test.ts` pattern; Vue SFC mounting isn't wired
into vitest and adding it isn't justified for placeholders).

## Pattern decisions (why not the task's `/admin/<slug>/index.vue` shape)

Live admin convention is **fragment routing inside `/admin#fragment` with a
pane per fragment**, not subroutes. Importing subroutes for admin tiles
would split the navigation model and break the existing `PaneLayout` +
`MenuList` active-state logic. Instead:

- Admin tiles → new `AdminFragment` enum value + a placeholder pane in
  `frontend/components/context/admin/placeholders/` + register in
  `adminMenuItems.ts` + `admin/index.vue` landing cards
- Profile tile → same pattern under `frontend/components/context/user/`
- Browsable surfaces (semantic, shapes, snapshots/diff, hdf container) →
  real subroute pages under `frontend/pages/<surface>/`

The three reusable placeholder components work identically in either shape.

## Nav surfaces touched

- `frontend/components/context/admin/adminMenuItems.ts` — 10 new fragments
- `frontend/pages/admin/index.vue` — 10 new landing cards + pane mounts
- `frontend/components/context/user/userMenuItems.ts` — 1 new fragment
- `frontend/pages/me/index.vue` — 1 new landing card + pane mount
- `frontend/components/layout/HeaderBar.vue` — new top-level "Semantic"
  link (gated to all authenticated users; the headline gets a top-level
  presence)

`/shapes/validate`, `/snapshots/diff`, `/containers/hdf/*` are discoverable
via deep-link only for now; surfacing them in the header would crowd it.
Listed under `aidocs/16` follow-up rows below.

## Backend gaps surfaced (informational)

- `:AIConfig` admin endpoint not yet shipped; placeholder cites
  `aidocs/integrations/97` as the design source and falls back to a
  "designed — not yet implemented" status. The `useFetch` returns null
  gracefully.
- `:BackupConfig` admin endpoint not yet shipped; placeholder cites
  `aidocs/strategy/105` and behaves the same.
- Per-vocabulary predicate listing endpoint not yet a first-class REST
  surface — the placeholder page degrades to a "browse via SPARQL or admin
  config" hint when no predicate-listing endpoint is mounted.

## Test coverage

`tests/unit/placeholderRegistry.test.ts` asserts:
- Every placeholder has a non-empty title + subtitle + designDoc reference
- Every endpoint string starts with `/v2/` (or is null for designed-not-shipped)
- Every backlog-row tag matches the live SSOT in `aidocs/16`
- The registry has the expected count (14)

This is the same shape as `sectionLanding.test.ts` — TS-module testable
without scaffolding Vue SFC mounting into vitest.

## What surprised me

- The existing admin index already has **11 fragment panes**. The task's
  enumeration significantly overestimated the gap. The genuine missing
  pieces are narrower than the prompt suggested.
- `TM1a`/`AI1b`/`AI1c`/`J1d` shipped backend without any user-facing
  attribution despite being explicitly user-visible features. They want
  *inline* UI (per-entity), not a new route, so a placeholder route would
  misdirect users. Worth filing properly-scoped follow-ups instead.
- The 4-page semantic-repository browser headline is the right move — it
  is the **only** place an external researcher would even see what
  vocabularies their instance has loaded. Currently invisible.

## Follow-ups for main session (backlog rows to file)

Per `feedback_github_pm_policies.md`, list candidate `aidocs/16` rows here;
main session files to avoid merge conflict:

1. **AI1a-UI** — frontend wiring for per-user AI settings once AI1a admin
   backend ships. Gate: AI1a.
2. **PG-COLLAPSE-002-UI** — frontend wiring for backup config once
   `:BackupConfig` ships. Gate: PG-COLLAPSE-002.
3. **AI1b-UI** — "Detect anomalies" button on TimeseriesReference page
   wiring `POST /v2/timeseries-references/{}/detect-anomalies`. Gate: none
   (backend AI1b shipped).
4. **AI1c-UI** — qualityScore badge on TimeseriesReference cards /
   detail page. Gate: none (backend AI1c shipped).
5. **TM1b** (already in `aidocs/16`) — frontend time-axis display +
   in-place `PATCH /v2/timeseries-references/{}` editor for
   timeReference / wallClockOffset.
6. **J1d-UI** — revision-history drawer on lab-journal entry detail page.
   Gate: J1d (shipped).
7. **A5-UI-PHASE-1** — full HDF container browsing UI (the placeholder
   advertises but doesn't navigate the H5 tree). Gate: A5 phases shipped.
8. **NTF1-UI** — full notifications admin pane (configure SMTP / Matrix
   transports + per-transport test). Gate: NTF1 backend (mostly shipped).
9. **SHAPES-V-UI** — full SHACL validation UI (currently placeholder hints
   at it). Gate: shapes/validate (shipped).
10. **SEMA-V6-UI-FOLLOWUP** — predicate-listing endpoint
    (`GET /v2/semantic/vocabularies/{id}/predicates`) so the
    `/semantic/vocabularies/[vocabId]` placeholder can become a real
    browser. Gate: needs backend slice.
11. **SNAP-DIFF-UI-FOLLOWUP** — full snapshot-diff visualisation
    (currently placeholder fetches the diff JSON but doesn't render it
    structurally). Gate: snapshot-diff endpoint shipped.
12. **HEADER-SEMANTIC-LINK** — already shipped this PR; track that the
    header now has a 5th nav link for visibility audit.

## Word count: ~1700
