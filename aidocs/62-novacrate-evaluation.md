---
stage: feature-defined
last-stage-change: 2026-05-26
---

# 62 — NovaCrate Evaluation for RO-Crate Metadata Editing

**Purpose.** Pre-decision assessment for R4: should shepard integrate
[NovaCrate](https://novacrate.datamanager.kit.edu/editor) — a
browser-based RO-Crate metadata editor from KIT Data Manager — and if
so, what is the right integration shape?

**Decision.** ADR-0003 (`aidocs/platform/63`) — "Open in NovaCrate"
external deep link only. No library dependency, no iframe embed.

**Audience.** Contributors evaluating RO-Crate tooling choices;
operator deciding whether to offer custom NovaCrate endpoints.

---

## 1. What NovaCrate is

NovaCrate is an open-source, browser-only RO-Crate metadata editor
maintained by the KIT Research Data Services group — the same group
that maintains the `edu.kit.datamanager:ro-crate-java` library
shepard already uses for export.

Key properties:

| Property | Value |
|---|---|
| Deployment | Single-page app, hosted at `novacrate.datamanager.kit.edu`; self-hostable |
| Packaging | Browser-only; no Maven artifact; no embeddable JS bundle |
| Data model | Opens `ro-crate-metadata.json` or a `.zip` RO-Crate; edits and re-saves locally |
| Network requirement | None for editing — "data stays on device" unless the operator adds upload endpoints |
| Auth surface | None — no auth integration possible from the outside |
| Deep-link protocol | `?url=<encoded-download-url>` opens a remote crate directly in the editor |
| License | MIT |
| Relation to `ro-crate-java` | Different product, same upstream group; NovaCrate is the consumer-side UI; `ro-crate-java` is the producer-side library |

---

## 2. Shepard's current RO-Crate state

`ExportService` + `ExportBuilder` produce a ZIP-packaged RO-Crate
server-side via `ro-crate-java` 2.1.0. The two export entry points are:

- **Legacy** `GET /shepard/api/collections/{id}/export` — streams the
  ZIP directly; byte-identical with upstream shepard 5.2.0.
- **R2 selective** `POST /collections/{id}/export` — adds an optional
  `ExportSelection` body (payload kinds, metadata booleans, per-field
  redaction). Both return `Content-Type: application/zip`.

The ZIP contains `ro-crate-metadata.json` plus per-entity JSON sidecars
and (optionally) payload blobs. A researcher who wants to edit the
metadata must download the ZIP, unzip it, edit `ro-crate-metadata.json`
in a text editor or NovaCrate, and re-zip — with no guidance from the
shepard UI.

---

## 3. Evaluation scope

The R4 question was: **Should shepard integrate NovaCrate, and if so,
at which integration depth?**

Four options were assessed.

### Option A — External "Open in NovaCrate" deep link ✅ chosen

Surface a button / link on the export-result panel that opens:

```
https://novacrate.datamanager.kit.edu/editor?url=<presigned-download-url>
```

NovaCrate fetches the ZIP from the presigned URL, unpacks it in the
browser, and lets the researcher edit. Saves are local-only (download
back to disk); re-import into shepard is a separate operation.

**Effort.** One `v-btn` in the export-result panel (or on the
collection detail page next to "Download as RO-Crate") + one paragraph
in `docs/help/export-rocrate.md`. No backend change.

**Prerequisites.** Presigned download URL (FS1c, shipped) or the
streaming GET endpoint. The streaming endpoint's URL is enough for
small crates; presigned is faster for large ones.

**Trade-offs.**

| Pro | Con |
|---|---|
| No code to maintain — NovaCrate is a third-party app | Researcher leaves the shepard UI; round-trip import not yet built |
| "Data stays on device" — no privacy concern | NovaCrate availability depends on upstream uptime (mitigated: self-hostable) |
| Matches the "edit metadata, re-download" workflow already implicit in the export ZIP | Deep-link requires CORS headers on the download endpoint — already satisfied by FS1f (presigned S3 URLs + CORS config) |
| Same upstream vendor as `ro-crate-java` — semantic compatibility guaranteed | — |

### Option B — iframe embed ❌ rejected

Embed the NovaCrate SPA in a Vuetify dialog via `<iframe
src="https://novacrate...">`.

Rejected because:
- NovaCrate's "data stays on device" guarantee does not hold when
  served inside a shepard-hosted iframe — the hostname changes and
  browser file-access APIs may behave differently.
- Same-origin restrictions would prevent the iframe from posting
  edited crate data back to the shepard parent without a postMessage
  protocol that NovaCrate does not provide today.
- Adds a fragile dependency on NovaCrate's UI layout (any
  NovaCrate redesign breaks the embed).

### Option C — NovaCrate-hosted upload endpoint ❌ rejected

Have NovaCrate receive a crate, expose an edit interface, and push the
edited crate back to a shepard `PUT` endpoint.

Rejected because:
- NovaCrate does not offer a "save to remote URL" endpoint today;
  the hosted instance would need a shepard-specific extension.
- Requires upstream coordination that is out of scope for this fork.
- Equivalent function is available via the round-trip-import path
  (R10, not yet designed) once that ships — at which point Option A's
  deep link + R10 import gives the same UX without coupling shepard to
  a NovaCrate extension.

### Option D — Round-trip via crate import ❌ out of scope

Build a `POST /collections/{id}/import-rocrate` endpoint that accepts
an edited ZIP and reconciles it with the live graph.

Rejected as a NovaCrate integration strategy because this is an
independent feature (`R10-rocrate-import`) that would be useful
regardless of NovaCrate. It is not a NovaCrate integration — it is an
import feature that NovaCrate export happens to feed into. Track under
R10, not R4.

---

## 4. Decision and follow-on tasks

**Adopted.** Option A. Cited in ADR-0003.

### Implementation tasks

| Task | File | Size | Status |
|---|---|---|---|
| "Open in NovaCrate" button on collection detail or export panel | `frontend/components/context/` (see CollectionDataPanel or export-result area) | XS | queued — R4-follow1 |
| `docs/help/export-rocrate.md` — task page explaining the export + edit flow | `docs/help/export-rocrate.md` | XS | queued — R4-follow2 (tracked in D1c) |

### Deep-link URL shape

```
https://novacrate.datamanager.kit.edu/editor?url=<encoded>
```

Where `<encoded>` is a URL-encoded download link for the RO-Crate ZIP.

- **Presigned URL (FS1c, S3 backend)** — preferred; 15-min TTL, no
  auth header required by the browser.
- **Streaming endpoint** — fallback for GridFS installs; requires the
  browser to pass the shepard API key as a query param
  (`?apiKey=...`) because NovaCrate cannot set custom headers.
  Only acceptable for installs where the API key is ephemeral
  (OIDC-issued short-lived JWT) — do not surface this flow for
  long-lived API keys.

### CORS note

The Garage / MinIO instance must have CORS configured to allow
`GET` from `novacrate.datamanager.kit.edu` (and the operator's
self-hosted NovaCrate origin if any). `docs/reference/file-storage.md`
(updated in FS1f) already documents the CORS stanza; R4-follow1 should
add a NovaCrate-specific paragraph.

### Self-hosting note

Operators that run NovaCrate internally can override the URL base
via a new deploy-time config key:

```
shepard.novacrate.editor-url=https://novacrate.datamanager.kit.edu/editor
```

Default is the public KIT-hosted instance. The key is optional; it
is deploy-time-only (no operator need to change it at runtime →
exception to the "always admin-configurable" rule per CLAUDE.md). This
config key ships as part of R4-follow1.

---

## 5. Alternatives that were NOT evaluated

- **Researchspace RDForms** — RDF-aware form builder; not RO-Crate
  specific; major scope increase.
- **Describo** — Australian ARDC's RO-Crate editor; equivalent UX
  to NovaCrate; no deep-link protocol documented; same Option-A
  analysis would apply.
- **In-house metadata-edit UI** — building a Vuetify form that edits
  the RO-Crate JSON graph directly inside shepard. Rejected as
  disproportionate scope for the "researcher wants to tweak a name"
  use case; revisit if NovaCrate becomes unmaintained or the
  round-trip-import (R10) lands and the friction of leaving shepard
  becomes observable.

---

## 6. References

- NovaCrate public instance: `https://novacrate.datamanager.kit.edu/editor`
- `ro-crate-java` upstream: `https://github.com/kit-data-manager/ro-crate-java`
- ADR-0003 (`aidocs/platform/63-architecture-decision-log.md`)
- R4 backlog row (`aidocs/16-dispatcher-backlog.md`)
- FS1c presigned-URL endpoints (`aidocs/34-upstream-upgrade-path.md`)
- D1c task pages (`aidocs/16` D1c row — `docs/help/export-rocrate.md`)
- R10 RO-Crate import (not yet designed)
