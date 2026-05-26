---
title: R4 — NovaCrate evaluation for RO-Crate metadata editing
stage: concept
last-stage-change: 2026-05-26
audience: contributors, frontend engineers, RDM feature owners
---

# R4 — NovaCrate evaluation for RO-Crate metadata editing

**Verdict: WATCH — revisit when NovaCrate's Cloud Frontend track matures.**

---

## §1 What was evaluated

| Artefact | Source |
|----------|--------|
| NovaCrate v1.11.1 | github.com/kit-data-manager/novacrate (KIT Data Manager) |
| RO-Crate spec 1.1 / 1.2 | researchobject.org/ro-crate |
| Describo crate-builder-component | github.com/describo/crate-builder-component (archived Aug 2025) |

The question: should Shepard adopt NovaCrate as an embedded component for
in-app RO-Crate metadata editing on the Collection detail page?

---

## §2 NovaCrate summary

NovaCrate is a standalone web application (not an npm library) built on
**Next.js 16 + React 19 + Tailwind CSS 4**. It validates RO-Crate 1.1 and
1.2, provides graph visualisation, ELN import/export, and live autofix.
All data is stored in the browser's Origin Private File System (OPFS) —
no server-side state.

Key facts for the integration decision:

| Dimension | Finding |
|-----------|---------|
| License | Apache-2.0 — compatible with this project's LGPL/MIT/Apache stack |
| npm package | `"private": true` — **not published to npm** |
| Framework | Next.js 16 + React 19 — incompatible with Nuxt 3 + Vue 3 |
| Embed path | None upstream; no `<script>` bundle, no web component, no iframe contract |
| RO-Crate versions | 1.1 full + 1.2 in progress |
| Maintenance | Active: 1,134 commits, v1.11.1 released May 2026, 22 open issues |
| Cloud Frontend edition | Listed as "concept" — no backend adapter contract is stable |

The `"private": true` flag is the hard stop: NovaCrate cannot be imported as a
dependency. There is no published build artefact to embed.

---

## §3 Alternative evaluated

**Describo `@describo/crate-builder-component`** was the only Vue-native
option in the ecosystem. It is MIT-licensed and was specifically designed to
be embedded inside other Vue applications, with the host responsible for
crate file I/O. However, it was **archived on 29 August 2025** (read-only,
no further maintenance). Adopting an archived dependency for a new user-facing
feature is not acceptable.

No other npm-published, Vue 3-compatible RO-Crate editor component was found
in the ecosystem as of 2026-05-26.

---

## §4 Integration shapes considered

Three integration shapes were assessed against the "watch" posture:

### 4a — External launch link (lowest effort, ships today)

The Collection detail page (`frontend/pages/collections/[collectionId]/index.vue`)
already has a "Download as RO-Crate" button. A companion link "Open in NovaCrate"
can point to `https://novacrate.datamanager.kit.edu` — the KIT-hosted public
instance. The user downloads the ZIP, then opens it in the browser app.

**Verdict:** viable as a zero-code addition. Does not solve the in-app editing
use case but is discoverable and useful for power users today.

**Implementation:** one `<v-btn>` with `href` pointing to the NovaCrate web app,
adjacent to the existing export button. Add a tooltip noting that the downloaded
ZIP must be opened manually in NovaCrate.

### 4b — iframe embed (medium effort, fragile)

NovaCrate's OPFS-based storage means iframe cross-origin storage isolation would
prevent the embedded instance from receiving the Shepard crate directly. There
is no documented `postMessage` API or URL-parameter ingestion protocol. This
shape would require upstream protocol work.

**Verdict:** requires upstream contribution; deferred.

### 4c — Contribute a Shepard backend adapter to NovaCrate (long-term)

NovaCrate's architecture separates `CrateService.d.ts` (the backend interface)
from the UI. When the Cloud Frontend edition matures, Shepard could ship an
adapter that backs NovaCrate's file operations against the Shepard REST API
(`GET /collections/{appId}/export`, `PATCH` for round-trip write-back).

**Verdict:** the correct long-term shape, but blocked on NovaCrate's Cloud
Frontend track advancing from "concept" to "shipping." Track upstream.

---

## §5 What Shepard already has

The existing export path satisfies the export half of the use case:

- `GET /collections/{collectionId}/export` → RO-Crate 1.1 ZIP (v1 compat surface)
- `POST /v2/collections/{appId}/export` → selective RO-Crate with payload filter
  and metadata booleans (R2, Phase 1)
- `POST /v2/collections/{appId}/export/regulatory-evidence` → BagIt + RO-Crate 1.1
  + PROV-O (TPL14)
- `RoCrateBuilder.java` assembles conformant `ro-crate-metadata.json` with root
  Dataset, per-DataObject Dataset nodes, `hasPart` links, and `shepard:appId`
  extension identifier

The **editing** half (modifying the crate's metadata from a UI and writing it
back to Shepard) is the gap. NovaCrate would address this gap if the integration
hurdles were resolved.

---

## §6 Verdict

**WATCH.** Do not adopt NovaCrate as an embedded component today. The blocking
constraints are:

1. `"private": true` — no npm package; cannot be imported as a dependency.
2. React 19 / Next.js — incompatible with Nuxt 3 + Vuetify 3 without a
   full framework bridge, which exceeds cost for this use case.
3. Describo (the Vue-native alternative) is archived.

**Recommended action (shape 4a):** Add an "Open in NovaCrate" external link
alongside the existing "Download as RO-Crate" button on the Collection detail
page. Zero new dependencies; ships in one commit. This is a UX improvement
for power users and establishes the NovaCrate relationship visibly.

**Revisit trigger:** when NovaCrate's Cloud Frontend track publishes a stable
`CrateService.d.ts` backend adapter contract and the first non-KIT adapter
ships, file a follow-on R4b row to design the Shepard adapter.

---

## §7 References

- `novacrate_github` — github.com/kit-data-manager/novacrate
- `describo_crate_builder_archived` — github.com/describo/crate-builder-component (archived 2025-08-29)
- `ro_crate_spec_1_1` — researchobject.org/ro-crate/1.1/
- Existing Shepard RO-Crate surface: `backend/src/main/java/de/dlr/shepard/v2/export/rep/RoCrateBuilder.java`
  and `backend/src/main/java/de/dlr/shepard/context/export/ExportBuilder.java`
- Backlog row: `aidocs/16-dispatcher-backlog.md` line 263 (R4)
