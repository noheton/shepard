# Lab Journal Reassessment + Jupyter Feasibility — Design

**Scope.** Reassess shepard's `LabJournalEntry` surface and design
how Jupyter notebooks fit into the journal/data-analysis flow.

**Status.** Design. No code or migration shipped.
**Snapshot date.** 2026-05-08.
**Originating items.** Issue **#507** ("Improve performance of
collection lab journal display"), issue **#368** ("Display lab
journal count on data object viewer"), backlog row in
`aidocs/16`, user request "Lab journal reassessment + feasibility
to include Jupyter notebooks for inline data analysis."

## 1. Today's lab journal

`LabJournalEntry` Neo4j entity (`backend/.../labjournal/entities/`):

- Free-text `journalContent: String`. **No structure** — not markdown,
  not HTML, not even line-break-aware on the render side.
- Attached to a `Collection`, `DataObject`, or `BasicReference` via
  `[:has_lab_journal]`.
- `createdBy`, `createdAt`. No edit history (write-once).
- Listed by `LabJournalEntryDAO.getEntriesByEntity`. No paging
  (covered separately by L6).
- Frontend renders as a flat scrolling list; #507 flags scale issues
  on Collections with hundreds of entries.

**What's missing for "data analysis companion" use:**

- Rich text / formatting (markdown body).
- Code blocks with syntax highlighting (R / Python / Julia / SQL /
  Cypher snippets are common in research debrief notes).
- Attachments — today the only way to attach an analysis artifact
  is to upload it as a separate `FileReference` and mention it by
  name in the prose, which breaks the narrative.
- **Notebook integration** — by far the highest-leverage gap. Most
  shepard users do their analysis in Jupyter; the journal entry
  today says *"see TR-004-analysis.ipynb in the files"* and the
  reader has to download, open Jupyter, find the notebook, and
  re-execute. The companion `examples/seed-showcase/notebooks/anomaly-analysis.ipynb`
  is exactly this pattern.

## 2. The "lab journal v2" reassessment

Three additive changes, no breaking ones:

| Change | Slice | What it does |
|---|---|---|
| **Markdown body** | J1a | `journalContent` interpreted as CommonMark + GFM (tables, fenced code, task lists). Render server-side via `commonmark-java` to sanitised HTML; client renders the HTML in a content-isolated frame. |
| **Inline notebook render** | J1b | Any `FileReference` attached to the same entity with `.ipynb` extension renders inline below the prose, statically. No live kernel — see §3. |
| **"Open in Jupyter" deep link** | J1c | Button next to each rendered notebook that opens the user's preferred JupyterHub (from `aidocs/36 §3.2` — `editor.preferredJupyter`) with the notebook URL pre-filled. |
| **Edit history** | J1d | Append-only revision log. Resolves "I corrected my journal entry but lost the original phrasing." Stored as a sibling `LabJournalEntryRevision` node. |
| **Pagination** | (L6) | Reuses L6's cursor pagination. Closes #507 by not loading hundreds of entries at once. |

**Backwards compat.** Existing entries with no markdown shape render
identically (CommonMark passes plain text through unchanged). No
data migration; the parser interprets old entries on the fly.

## 3. Jupyter — what's feasible vs what's not

Three architectural shapes were considered:

### 3.1 (A) Static notebook render — recommended

Render `.ipynb` to HTML server-side via `nbconvert` (Python — runs
once per notebook upload, output cached as a `ShepardFile` sibling).
Or client-side via [nbviewer.js](https://github.com/jupyter/nbconvert)
/ [react-nb-viewer]. **Recommend client-side** to avoid a Python
runtime in the backend.

What it gets you:

- Cells, outputs, plots, tables — exactly as the user saw them at
  the time of save.
- No kernel, no execution, no security model beyond standard XSS
  isolation (HTML rendered inside an iframe with `sandbox`).
- Fast, cacheable, works offline once cached.

What it doesn't get:

- **No re-execution.** Output is whatever was committed in the
  notebook file.
- **No interactivity.** Plotly / ipywidgets that require a kernel
  show their last-rendered state, not a live one.

This is the right v1 — it covers 90% of "I want to see what they
analysed."

### 3.2 (B) Live kernel — deferred

Embed JupyterHub or run a kernel-per-user service. Massive scope:
authentication bridge (similar to A5e's HSDS shape), per-user
sandboxing, kernel state TTL, resource quotas, security review
(arbitrary code execution against shepard's data). **Deferred** —
the right answer is the deep link from §2 J1c, which leverages
the user's existing JupyterHub.

### 3.3 (C) External link only — fallback

If neither A nor B is viable in a given deployment, fall back to
the existing FileReference pattern: the user downloads the
notebook and opens it locally. **Always available** as the
zero-effort path.

### 3.4 Recommendation: A + C, link to user's Jupyter

- Static render for the inline view.
- Deep link to the user's preferred Jupyter URL for "I want to
  re-run this."
- Download fallback as before.

## 4. Backend changes

### 4.1 Entity

`LabJournalEntry` gains nothing new at v2 — markdown is interpreted
on read, the column stays `String`. Edit history (J1d) adds a
sibling node + relationship:

```java
@NodeEntity
public class LabJournalEntryRevision {
  @Property private String previousContent;
  @Property private Instant revisedAt;
  @Property private String revisedBy;
  @Relationship(type="REVISION_OF") private LabJournalEntry entry;
}
```

### 4.2 Render endpoints

- `GET /v2/lab-journal/{appId}/render` — returns sanitised HTML
  with the markdown rendered. `Content-Type: text/html`. Frontend
  can fetch this directly; or use a `LabJournalRenderService` that
  produces the HTML inline in `LabJournalEntryIO`.
- `GET /v2/lab-journal/{appId}/notebooks` — returns the list of
  attached `.ipynb` FileReferences with their static-render URLs.

Both endpoints land under **`/v2/`** per the API-version policy in
`CLAUDE.md` — upstream's `/shepard/api/lab-journal/...` paths stay
byte-identical for clients built against upstream.

### 4.3 Notebook static render

Two paths:

| Where | Trigger | Caching |
|---|---|---|
| Client-side | Vue component requests the notebook bytes via existing FileReference download endpoint, renders with [nbviewer-js] | Browser cache |
| Server-side (later, if needed) | `nbconvert` containerised sidecar; pre-render on FileReference create | Mongo cache key = `<oid>-rendered.html` |

**Start client-side.** Adds zero backend dependencies, ships with
the frontend bundle, performance is fine for typical research
notebooks (< 10 MB). Server-side is a follow-up if very large
notebooks become common.

## 5. Migrations

- **No data migration needed.** Markdown interpretation is
  read-side; old entries already render correctly under CommonMark
  (plain text is valid markdown).
- **Tracker rows** in `aidocs/34`:
  - J1a: ZERO (additive read-side parsing).
  - J1b/c: ZERO (frontend additions, FileReference path unchanged).
  - J1d: ZERO (new sibling node, no backfill).

## 6. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **J1a** | Markdown body interpretation + sanitisation + `GET /lab-journal/{appId}/render`. | S | None |
| **J1b** | Inline `.ipynb` static render in the journal-entry view (client-side via nbviewer-js). | S | J1a |
| **J1c** | "Open in Jupyter" deep link using `editor.preferredJupyter` from user settings (`aidocs/36 §3.2`). | XS | aidocs/36 U1d |
| **J1d** | Edit history (`LabJournalEntryRevision`). | M | None |
| **J1e** | (deferred) Server-side `nbconvert` for very large notebooks. | M | When notebook size becomes a real performance gap. |
| **J1f** | (deferred) Live kernel via JupyterHub bridge. | XL | Out of scope; mentioned for completeness. |

Recommended order: **J1a → J1b → J1c → J1d**. J1a + J1b together
make the journal a real research-debrief surface; J1c closes the
"I want to re-run this" loop with zero backend work.

## 7. Cross-references

- **Issues:** #507 (lab journal display perf), #368 (lab journal
  count on data object viewer).
- **aidocs:** `aidocs/16` (L6 pagination dependency); `aidocs/36
  §3.2` (`editor.preferredJupyter` setting wired here at J1c);
  `aidocs/30` (provenance — notebook execution outputs are a
  natural lineage source for future work); `aidocs/14` (semantic
  annotations could ride on lab journal entries — out of scope
  here, mentioned for the connection).
- **Backlog:** new **J1** umbrella + J1a-J1f sub-IDs in
  `aidocs/16`, gated only on the L6 pagination work for the
  scale fix in #507.
