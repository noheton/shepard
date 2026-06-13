---
stage: audited-by-personas
last-stage-change: 2026-06-13
audience: frontend, UX, product, docs, dispatcher
---

# UX consult 2026-06-13 — personas, journeys, and the dual-doc IA for the docs+verification program

**Auditor:** Core Tech & UX Auditor (Role 1) — focused consult, NO code changes.
**Trigger:** operator is mounting a program to (a) replace every placeholder UI stub
with real functionality, (b) Playwright-e2e-verify every feature, and (c) document
EVERY feature in the existing two-track docs system (`docs/help/` basic +
`docs/reference/` advanced). This doc defines the **personas, journeys, and dual-doc
information architecture** each per-feature doc page and each e2e must be written
against — so the doc-writing and verification agents work from a real journey, not a
vacuum.
**Live walk:** YES — authenticated Playwright walk of `https://shepard.nuclide.systems`
as `flo / flo-demo` at **3840×2160** (operator hardware). Script:
`e2e/scripts/ux-journeys-walk-2026-06-13.mjs`; screenshots:
`aidocs/agent-findings/screenshots/ux-journeys-2026-06-13/` (11 PNG + console/HTTP-error log).
**Builds on:** `aidocs/agent-findings/ux-shapes-displays-and-journeys-2026-06-12.md`
(my own prior journeys doc — J1–J4 + the shapes-render breakage; not re-litigated here,
cross-referenced), `aidocs/frontend/01-user-research-findings-2024.md` (the canonical
5-phase journey + the four-role split), `aidocs/ops/49-in-app-user-docs.md` (the
two-track `docs/help` + `docs/reference` IA + the `/help` route + screenshot pipeline),
CLAUDE.md Roles 9 (Reluctant Senior) & 10 (Digital Native), and the memory rule
"three-audience docs" (admin + user + plugin).

---

## What I found (live, 2026-06-13)

The instance is healthy and the read surfaces are strong. **Top-nav (confirmed live):**
`Home · Collections · Projects · Containers · Tools · Admin` + Search box + notification
bell + avatar (`F`) + Sign Out. `/me` and `/admin` are hub-tile grids (good — both pass
the nav-reachable test). `/tools` is a 6-tile grid (Vocabularies, SPARQL playground,
Shape validator, Snapshot diff, Shapes render playground, Form preview).

Five facts the docs+verification program must absorb up front:

1. **`/help` exists and is genuinely comprehensive** (≈55 entries spanning both tracks
   — Overview, Getting started, every help task, every reference primitive, plugin
   quickstarts) **but it is NOT in the top-nav.** It is reachable only by typing
   `/help`. By the repo's own "top-nav reachable before beta" rule, the in-app docs
   feature is itself stuck at `alpha`. The single highest-leverage docs fix is a
   top-nav (or avatar-menu) **Help** entry + a context "?" affordance per page (D1g).
2. **`/snapshots` 404s** (`[nuxt] error … Page not found: /snapshots`) — a Tools
   concept (Snapshot diff) has a dangling top-level route. Snapshots are reachable
   in-context (collection `SnapshotsPane`) but the bare route is a dead end.
3. **`GET /v2/users/me/preferences` → 401 on the landing page** for the logged-in
   user, plus a recurring **hydration mismatch** console error on most pages — both
   carried over from the 2026-06-12 walk (UX612-M4/M5/m6 class). These pollute the
   console the verification e2es will run against; silence them or the e2e
   error-assertions become noise-tolerant and stop catching real regressions.
4. **4K dead-space.** Every page left-aligns content into roughly the left ~40 % of a
   3840-px viewport (see `01-landing.png`, `04-tools.png`) — the operator's actual
   hardware shows a half-empty screen. Doc screenshots captured at 4K will show this;
   the screenshot pipeline (`aidocs/ops/49 §3`) should pin a sane content max-width or
   capture at a representative width, else every screenshot looks broken.
5. **The shapes-for-displays render path is still broken end-to-end** (UX612-C1/C2/M6,
   diagnosed 2026-06-12). Any journey or doc page that ends in "see the rendered view"
   currently dead-ends. The doc for view-recipes/Trace3D MUST NOT be written as if the
   happy path works — write it against the *target* flow and flag the live gap, or hold
   the page until the C1/C2 fix lands.

---

## 1. Personas (the tight set the docs+verification program serves)

Reconciled from: the 2024 four-role split (researcher-programmer / researcher /
project-lead / admin), CLAUDE.md Roles 9 & 10, and the four live use cases (MFFD,
LUMEN, BTKVS docket, microsections). Five personas — each with an **IT-skill level**,
a **primary goal**, the **doc track they live in**, and the **showcase that grounds
them**. (The 2024 anonymity rule is honoured — these are archetypes, not interviewees.)

| # | Persona | IT skill | Primary goal | Doc track (home) | Grounding showcase |
|---|---------|----------|--------------|------------------|--------------------|
| P1 | **Sina — propulsion test engineer** (the *Reluctant Senior*, R9 flavour) | LOW–MED. Excel + NFS folders are her current "RDM". Mouse-only, will revert at any >3-click friction. | "Did TR-006 clear TR-004's vibration anomaly? Show me the trace." | **Basic** (`docs/help/`) — task pages, screenshots, zero jargon | LUMEN (J1) |
| P2 | **Jonas + Greta — AFP process eng + quality eng** (shop-floor / IME, R4 flavour) | LOW (Jonas, ruggedised terminal) / MED (Greta, audit). Big targets, scan-friendly, EN 9100 evidence. | "Was the ply-5 anomaly rework NDT-cleared? Pull the audit chain." | **Basic** primary; Greta dips into **Reference** for the evidence-pack/provenance endpoints | MFFD (J2) |
| P3 | **Nils — composites lab engineer** (researcher, structured-data) | MED. Comfortable with forms + templates, not with REST. | "Record plate I123's siliconization step + post-analysis; render the docket for the group lead." | **Basic** for entry/render; **Reference** for template/SHACL shape internals | BTKVS docket (J3) |
| P4 | **Maren — materialography analyst** (researcher → notebook user) | MED–HIGH. Lives in Jupyter; touches the API edge. | "Is PH2940-04's FVF in range? Compare carbon vs flax on PH2940-01." | **Basic** for the in-app preview; **Reference** for the Jupyter/notebook + FR1b singleton API | Microsections (J4) |
| P5 | **Théo — digital-native postdoc** (researcher-programmer, R10) | HIGH. `requests.get` → `pd.DataFrame`; API-first; MCP daily. Reads `docs/help` for 90 s then goes straight to REST. | "Load TR-004's channels into a DataFrame in 5 lines; script bulk ingest." | **Reference** (`docs/reference/`) — entity model, endpoints, curl/Python examples, MCP | LUMEN + microsections (cross) |
| P6 | **Otto — instance admin / project lead** | MED–HIGH ops. | "Configure a plugin, hand out a role, audit who changed what, cut a release." | **Reference** + the **admin** third track (`docs/admin/runbooks/`, CLI parity) | all (operator across showcases) |

Track-home rule of thumb: **P1–P4 land in `docs/help/` first** and only follow a
"For the full model / API →" link into `docs/reference/` when they need depth. **P5–P6
start in `docs/reference/`.** The basic→advanced UI toggle
(`useAdvancedMode.ts`, advanced is a strict superset) mirrors this exactly: P1–P4 are
basic-mode users, P5–P6 run advanced mode. **Every help page must be self-sufficient
for a basic-mode (advanced-OFF) user** — never tell P1 to "expand the Advanced panel."

---

## 2. Journeys per feature area (entry point · steps · success state · friction)

Click-counts are from the live walk where verifiable, else from the page/component
code + the 2026-06-12 walk (flagged). "Entry must be top-nav-reachable" per CLAUDE.md.
Dead-end / >3-click flags drive both the e2e priority and the doc page's "you are here"
opening line.

| Area | Entry point (top-nav path) | Canonical steps | Success state | Click-count / dead-end flag |
|------|----------------------------|-----------------|---------------|------------------------------|
| **Collections** | Collections (top-nav) → row | list → open → read hero/metadata/DO table | Collection detail rendered; DO table + lineage visible | 2 clicks. ✔ Strong. |
| **DataObject** | Collection → sidebar tree OR DO table row | open DO → annotations/lab-journal/refs panels | DO detail with all per-kind ref panels | 3 clicks. ✔ |
| **FileReference (FR1b singleton)** | DO detail → Data References panel → row | view → preview/download; "Open in Jupyter" for notebook refs | inline preview / file fetched; no pointless picker | 3–4 clicks. ✔ (singleton = no file-picker, correct shape). ⚠ image preview at 4K unverified (microsections not seeded). |
| **FileBundleReference** | DO → refs panel → bundle row → inner-file picker | view → pick inner file → action | inner file resolved | 4–5 clicks. ⚠ extra picker step is the bundle tax (FR1b rule). |
| **TimeseriesReference** | DO → refs panel → "Graph & Metrics" | open ref detail → chart + channel stats | interactive chart, channels toggleable | **DEAD END (UX612-C1)** — ref detail page dead under both id shapes. CRITICAL. |
| **TimeseriesContainer** | Containers (top-nav) → timeseries → row | open → channel table → expand row → preview | per-channel preview chart + annotations | 4 clicks. ✔ chart works; ⚠ no container-level "Visualize" CTA (UX612-m4); ✗ `/channels` empty-appId 405 (UX612-M6). |
| **StructuredDataReference** | DO → refs panel → structured row | view JSON / render via recipe | structured data shown | ⚠ entry & review are raw-JSON; render path blocked by C2. (J3) |
| **SpatialDataReference** | DO → refs panel → spatial row | view → map/3D | spatial view | ⚠ backend shipped, FE viewer pending (task #79). alpha. |
| **Tools / shapes / view-recipes** | Tools (top-nav) → tile, OR (better) DO detail → "View" | pick template → bind → render | rendered 3D/heatmap | **BROKEN (UX612-C2)**: ≥14 clicks + numeric-id transcription + ends in false-blame error. CRITICAL. Target = 2 clicks via in-context "View" + `/applicable`. |
| **Templates / template editor** | `/admin#templates` fragment (or `/me` Templates tile) | open → create/edit recipe/shape | template saved | ⚠ Tools "Create template…" routes to hard 404 `/admin/templates` (UX612-M2). MAJOR. Templates not top-nav, only via Admin/Me hub. |
| **Shape validator / Form preview** | Tools tile (fallback) — should be DO/template detail (in-context-first rule) | pick shape → validate / preview form | conformance report / form | ✔ reachable; ⚠ global-menu-only entry keeps it alpha per tools-in-context rule. |
| **SPARQL / Vocabularies** | Tools tile + Admin "SPARQL Playground" | query / browse terms | results | ✔ reachable; ⚠ in-context "Query this collection" entry missing (alpha per rule). |
| **Snapshots** | Collection → SnapshotsPane (in-context) | list → compare | snapshot diff | ✔ in-context; ✗ bare `/snapshots` route 404s (live, this walk). |
| **Search** | Search box (top-nav) → `/search` | type query → results / Advanced query | hits | 1 click. ✔ present; depth unverified. |
| **Provenance / activity** | DO/Collection detail panel + Admin "Activity Log" | view graph / filter | prov graph / log | ✔ reachable both in-context and admin. |
| **Admin (each tile)** | Admin (top-nav) → tile | open pane → configure | config applied | ✔ 30+ tiles, all nav-reachable (good). P6 home. |
| **Plugins** (AAS, minters, spatial, KRL, video, HDF, Unhide, JupyterHub) | DO "Add reference" menu (payload kinds) + Admin "Plugins" + `/help` plugin pages | per-plugin journey | per-plugin success | ⚠ mixed; each plugin owns 3 docs (`reference/quickstart/install`) per CLAUDE.md plugin-docs rule — verify each is nav-reachable. |
| **In-app docs (`/help`)** | **NONE in top-nav** — typed URL only | — | — | **DEAD END for discovery.** The docs feature itself is alpha. Add top-nav/avatar "Help" + per-page "?". HIGHEST docs-program fix. |

**Cross-journey observation (unchanged from 2026-06-12, reconfirmed):** the four
showcase journeys all converge on one missing affordance — an in-context, auto-bound
**"View"** of the entity the user is standing on — and on one missing discovery
affordance — a **top-nav Help entry**. The docs program cannot paper over the first
(the page must flag it); it can and should drive the second.

### Top-5 journeys I walked live (this session)
Landing (✔), Collections list (✔), Containers (✔), Tools grid (✔), Admin hub (✔),
plus Me/Search/Semantic/Help. The collection→DO→reference→render deep path was
exhaustively walked on 2026-06-12 (19 screenshots) and is not re-walked here to avoid
duplicating that evidence; its breakages are cited, not re-discovered.

---

## 3. Dual-doc information architecture

### 3.1 Diátaxis mapping decision

Shepard's existing two-track split is **already a Diátaxis system collapsed to two
files per feature**, and it maps cleanly along Diátaxis's action↔cognition axis:

| Diátaxis quadrant | Axis | Shepard track | Persona |
|-------------------|------|---------------|---------|
| **Tutorial** (learning-oriented, action) | acquisition × action | `docs/help/` (the page's *intro + first walkthrough*) | P1–P4 (first encounter) |
| **How-to** (task-oriented, action) | application × action | `docs/help/` (the page's *numbered steps*) | P1–P4 (returning) |
| **Reference** (information-oriented, cognition) | application × cognition | `docs/reference/` (entity model + endpoint tables) | P5, P6 |
| **Explanation** (understanding-oriented, cognition) | acquisition × cognition | `docs/reference/` (the "Why / How it works" section) | P5, P6 |

**Decision: keep two physical files, but make each track explicitly own two Diátaxis
modes internally rather than splitting into four files.** Four files per feature ×
~40 features = 160 pages = unmaintainable for this team; the existing `help`/`reference`
split already cleaves the hard boundary (action vs cognition / basic vs advanced). So:

- **`docs/help/<task>.md`** carries **tutorial + how-to**. It opens with a 1–2 sentence
  "what you'll achieve" (tutorial framing) then numbered how-to steps. **No** entity
  models, **no** endpoints, **no** "why".
- **`docs/reference/<primitive>.md`** carries **reference + explanation**. It opens with
  a "What it is / Why it exists" (explanation) then the entity/endpoint reference
  tables. **No** click-by-click hand-holding.
- The hard Diátaxis rule still binds: **never mix modes within a section.** A how-to step
  must not explain the data model inline; a reference table must not narrate a workflow.
  Cross-link instead.

This honours the "three-audience docs" memory rule by treating **admin** as a *third*
track (`docs/admin/runbooks/` + `docs/reference/admin-*.md` + plugin `install.md`), and
**plugin** docs as self-hosted per the CLAUDE.md plugin-docs rule (`plugins/<id>/docs/`).
P6 (admin) reads the third track; the two-track help/reference split serves P1–P5.

### 3.2 Reusable page templates

#### Template A — BASIC (`docs/help/<task>.md`) — tutorial + how-to, for P1–P4

```markdown
---
title: <Verb the task, e.g. "Plot timeseries data">
description: <One line a researcher would search for>
permalink: /help/<task-slug>/
layout: default
audience: user
journey-phase: <Discovery|Install|Create structure|Import data|Working with data>
persona: <P1|P2|P3|P4>          # primary persona this page serves
related-reference: /reference/<primitive>/
---
# <Verb the task>

<1–2 sentences: what you'll achieve and why you'd want to. No jargon. Tutorial framing.>

## Before you start
- You need: <permission / a Collection with X / nothing>.   <!-- omit if none -->

## Steps
1. From the top nav, click **<NavEntry>**.                  <!-- entry MUST be nav-reachable -->
2. <Action> → <what you see>.                               <!-- ≤ 5 numbered steps -->
3. <Action> → **<success state in bold>**.

![<alt>](../assets/screenshots/<data-target-id>.png)        <!-- Playwright-pipeline marker -->

## Tips
| Action | Result |                                           <!-- optional, scan-friendly -->
|---|---|

## If something goes wrong
- <Symptom> → <one-click fix or link to a troubleshooting page>.

> **Want the full model or the API?** See the
> [<primitive> reference](../reference/<primitive>.md).
```

Rules for Template A: ≤ 5 steps to success; every step is a UI action a mouse-only
basic-mode user can do; the entry step names a **top-nav** item; exactly one
screenshot per major step using a `data-target` marker (so the §3 pipeline fills it);
**zero** entity names, endpoints, IDs, or "why it works". If the happy path is broken
on live (e.g. Trace3D / C2), the page carries a one-line `> ⚠ Note:` and the e2e is
marked `expected-fail` until the fix lands — never ship a help page that lies.

#### Template B — ADVANCED (`docs/reference/<primitive>.md`) — reference + explanation, for P5/P6

```markdown
---
layout: default
title: <Primitive, e.g. "Timeseries reference">
permalink: /reference/<primitive>/
audience: user
api-version: v2                 # this fork's surface; note v1 only for upstream byte-compat
related-help: /help/<task-slug>/
---
# <Primitive>

## What it is & why it exists                                <!-- Explanation quadrant -->
<2–4 sentences: the concept, the substrate it lives in, the design rationale.
Link the design doc: see `aidocs/...`.>

## Entity model
| Field | Type | Notes |                                    <!-- the appId/shepardId, statuses, edges -->

## Endpoints (`/v2/`)                                        <!-- Reference quadrant -->
| Method · Path | Purpose | Auth |
| `POST /v2/<resource>` | … | … |
<Note v1 byte-compat path only if it exists, marked frozen.>

### <Endpoint> — worked example
```bash
curl -H "X-API-Key: $KEY" "https://<host>/v2/<resource>/<appId>"
```
```python
# 5-line P5 path: load into a DataFrame
import requests, pandas as pd
r = requests.get(URL, headers={"X-API-Key": KEY}); df = pd.DataFrame(r.json()["rows"])
```

## Config keys / admin                                       <!-- only if the feature has knobs; P6 -->
| Key | Default | Runtime-mutable? | Admin endpoint |

## Limits & errors
| HTTP | Cause |

> **Just want to do the common task?** See
> [<task>](../help/<task-slug>.md).
```

Rules for Template B: lead with explanation (the "why"), then reference tables; every
endpoint gets one worked request/response; identifiers are `appId` (UUID v7) per the
frontend/plugin-v2-only rules; v1 paths appear only when they are the frozen upstream
carrier, marked as such; config keys cite the `:*Config` singleton + admin REST + CLI
parity per the admin-knobs rule. Plugin reference pages live in `plugins/<id>/docs/`,
not here (link them from `docs/reference/plugins.md`).

**Bidirectional linking is mandatory** (the `related-reference` / `related-help`
front-matter + the closing block in each template). A P1 who needs depth never hits a
dead end; a P5 who wants the quick path never scrolls reference prose. This is the
`aidocs/ops/49 §2.2` "Track-A always links to Track-B and back" rule made concrete.

---

## 4. Top 5 journey fixes (ordered by effort × value)

1. **Add a top-nav (or avatar-menu) "Help" entry + per-page "?" deep-link into
   `/help`.** *Effort: hours.* The in-app docs are comprehensive but undiscoverable —
   the docs-program's own output is currently `alpha` because nobody can reach it.
   This is the cheapest, highest-leverage fix and a prerequisite for the whole
   "document every feature" effort to pay off. (D1g in `aidocs/ops/49`.)
2. **Fix the three id-shape breaks (UX612-C1/C2/M6) feeding the TimeseriesReference +
   shapes-render journeys.** *Effort: days.* Without this, every "see the chart / see
   the rendered view" journey dead-ends, and the matching doc pages cannot be written
   truthfully or e2e-verified. This is the gating dependency for J1–J4's payoff steps.
3. **Silence the guaranteed console/HTTP noise (preferences 401, avatar/publications
   404, hydration mismatch).** *Effort: hours.* The verification program runs e2es that
   assert on console cleanliness; a permanently-noisy console forces the e2es to ignore
   errors and stops them catching real regressions. Clean the baseline first.
4. **Pin a content max-width (or capture screenshots at a representative width).**
   *Effort: hours.* At the operator's 4K hardware every page uses ~40 % of the screen;
   doc screenshots will look broken and P1's trust erodes (the §3 trust-erosion risk).
   Either constrain content width or set the screenshot pipeline viewport to ~1920 with
   a documented note.
5. **Wire the in-context "View" + `/admin#templates` route fix (UX612-M2) and the
   tools-tile relabel (UX612-m1, "Shapes render playground" still says "Render URDF /
   mesh / spatial-shape previews").** *Effort: days for View, hours for the route +
   label.* Turns the 14-click view journey into 2 clicks and removes two hard-404
   dead-ends from the Tools menu — the journeys most likely to be clicked by a curious
   first-time user.

---

## What surprised me
- **The richest docs surface in the app has no front door.** `/help` carries ~55
  entries across both tracks and reads well — and it is reachable only by typing the
  URL. The team built the library and forgot the door.
- **The two-track split the operator already has is a Diátaxis system in disguise** —
  it cleaves exactly on action↔cognition. The doc-writing agents don't need a new
  framework; they need the two existing tracks to each own their two modes cleanly and
  never mix.
- **At 4K the app is half-empty.** The operator's own hardware is the worst-case
  viewport for the very screenshots the program will publish.
- **`/snapshots` 404s** while snapshots work fine in-context — a reminder that
  "feature works" and "every route to it resolves" are different gates, which is
  exactly what the verification program is for.

---

## Backlog rows to file (aidocs/16)
`DOCS-NAV-HELP-1` (top-nav/avatar Help entry + per-page "?" — fix #1);
`DOCS-4K-SCREENSHOT-1` (content max-width / pipeline viewport — fix #4);
`DOCS-DIATAXIS-TEMPLATE-1` (adopt Templates A+B + front-matter `persona`/`journey-phase`/
`related-*` keys across `docs/help` + `docs/reference`);
`DOCS-PERSONA-FRONTMATTER-1` (tag every existing help/reference page with its persona +
journey-phase). Render-path breakages (`UX612-C1/C2/M2/M6/m1`) already filed 2026-06-12.

## External patterns cited
- Diátaxis framework — four documentation types on the action↔cognition / acquisition↔
  application grid: <https://diataxis.fr/> and <https://diataxis.fr/start-here/>.
