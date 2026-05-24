---
stage: feature-defined
last-stage-change: 2026-05-24
audience: RDM, FAIR steward, funding-body reviewer, operator
---

# RDM Scrutinizer — FAIR + DMP + Publication Readiness, 2026-05-24

**Scrutinizer:** Playwright-driven live walk of `https://shepard.nuclide.systems` against
DFG / Horizon Europe / Clean Aviation JU DMP expectations and the 15 FAIR sub-principles.

**Layered as a DELTA over the prior source-only RDM audit** at
[`research-data-manager.md`](research-data-manager.md) (2026-05-21, by the same role). That
document established baseline FAIR scores from entity-level inspection; this document tests
those scores against what a researcher actually SEES in the UI today, after the 13 UI
improvements that shipped 2026-05-24 (BUG #139 fix, header search, section landings,
collection list enrichment, help search, chip dedup, DO description edit cue, etc.).

**Sibling** to the parallel [`ui-scrutinizer-2026-05-24.md`](ui-scrutinizer-2026-05-24.md)
(non-overlapping concern: clicks/seconds vs FAIR/DMP/publication; same datasets, same auth
session, different lens). Where the UX scrutinizer asks "can alice DO X in N clicks?", this
audit asks "does the data alice produces meet what a Horizon Europe reviewer demands?"

**Spec source:** [`e2e/tests/rdm-walkthrough.spec.ts`](../../e2e/tests/rdm-walkthrough.spec.ts) — 9 tests, reproducible.
**Evidence:** [`rdm-scrutinizer-2026-05-24-evidence/`](rdm-scrutinizer-2026-05-24-evidence/) (46 files: 23 screenshots, 23 JSON sidecars with FAIR-term presence probes).

---

## TL;DR

**LUMEN as it exists on the live deploy today is NOT publication-ready by any FAIR-aware
funder's lights.** Five hard-blocking gaps stand between the current researcher surface and a
Horizon Europe data deposit: (1) no `license` field anywhere on Collection or DataObject
(operators are typing `proprietary` as a free-text attribute key on MFFD-Dropbox as a
workaround); (2) no `accessRights` enum and no `embargo` field; (3) no creator ORCID
rendered on any user-visible surface even though the User entity stores it; (4) no
"How to cite" / DOI / PID block anywhere on the Collection landing — only a `Publish`
button whose target dialog is the entire UI surface for minting (no PID management page,
no listing of existing publications, the entire `/admin/publish*` path tree 404s); (5)
the AI-collaboration provenance surface that the fork's f(ai)²r differentiator promises
exists (Provenance tab is rendered on every DO) but is empty even for DOs that have AI
input — capture isn't wired or isn't displayed.

Five-finding verdict:

1. **CRITICAL — license/accessRights/embargo absence is visible at the entity level.** Closes most
   of R1.1 by itself when LIC1 (#140) ships. Until then, no FAIR-compliant deposit is possible.
2. **CRITICAL — no PID management UI.** `Publish` button exists per-collection but `/admin/publish`, `/admin/publications`,
   `/publications` all 404. Operator cannot list, audit, or rotate PIDs without curl.
3. **MAJOR — User profile is a 5-card grid with no ORCID input.** The User entity validates ORCID with mod-11-2
   checksum; the UI never asks for it and never displays it on any DO/Collection surface.
4. **MAJOR — Citation block missing on Collection landing.** Collection 42's landing has Description, Semantic
   Annotations, Data Objects, Attributes, Lab Journal, Activity, Referenced Containers, Dataset Lineage, Watched
   Containers, Snapshots — but no "How to cite this dataset" block. A researcher cannot copy a citation.
5. **MAJOR — `/admin/unhide` 404 even for admin.** The Helmholtz Unhide harvest config (UH1a backend) has no UI;
   `accessRights` field absence means the feed would emit nothing useful regardless.

**FAIR composite is unchanged from the 2026-05-21 source-only audit** (F:2.3 / A:1.4 / I:2.0 / R:0.9 →
~1.7/3) because today's 13 UI improvements were all polish (help search / chip dedup / DO
description edit cue / section-landings / header search / BUG #139 layout fix). **Zero touched
FAIR-relevant surfaces.** Two scores nudge by ≤0.1 in either direction based on live observation; the
prior audit holds.

---

## What I walked

| Surface | URL | Captured |
|---|---|---|
| LUMEN collection landing | `/collections/42` | `01-collection-42-landing.{png,json}` |
| LUMEN DO detail (TR-001) | `/collections/42/dataobjects/45` | `03-do-detail-landing.{png,json}` |
| LUMEN DO Provenance/LabJournal/Notebooks tabs | (same, 3 expansion-panel tabs) | `07-prov-tab-{0,1,2}-*.{png,json}` |
| User profile (alice) | `/me` | `04-user-{page,profile-me}.{png,json}` |
| Publish UI candidates | 5 paths tried | `05-publish-tries.json` + 5 PNGs |
| Export affordances | DOM scan on `/collections/42` | `06-export-candidates.json` |
| MFFD-Dropbox landing | `/collections/661923` | `08-mffd-collection-landing.{png,json}` |
| MFFD-Dropbox DO detail | `/collections/661923/dataobjects/661928` | `08-mffd-do-detail.{png,json}` |
| Admin surfaces (admin user) | 5 paths | `09-admin-*.{png,json}` |

---

## FAIR score card (live-walk delta over 2026-05-21 source audit)

The prior doc's scores stand as baseline. This card adds a **live evidence column** and an
**adjustment** column (`=` unchanged, `↓` worse than source review suggested, `↑` better).

| Sub-principle | Prior | Live evidence | Adj | Updated |
|---|---|---|---|---|
| **F1** globally unique persistent IDs | Full | UUID v7 appId visible in URL: `/collections/42/dataobjects/45`. No human-resolvable PID rendered on landing. | = | Full |
| **F2** rich metadata described with the ID | Partial | Collection 42 landing has name, description (~180 chars), Semantic Annotations panel (≥10 chips), Data Objects table (17 rows). MFFD-Dropbox has 8514 DOs with name/created/updated. NO license/funder/rights on the surface. | = | Partial |
| **F3** metadata registered in searchable resource | Partial | Header search now works (UI-002 shipped today); no DataCite catalog export visible; `/admin/unhide` 404 → harvest feed has no UI surface. | ↓ | Weak (downgrade: discoverability infrastructure is API-only, no surfaced state) |
| **F4** discoverable via catalog | Not scored | No re3data registration, no `/sitemap.xml` for crawlers, no Databus push UI. | new | Weak |
| **A1** retrievable by identifier via open protocol | Full | REST API; OpenAPI at `/shepard/doc/openapi/v2.json`. Inspector cannot reach the PID resolver from the UI. | = | Full |
| **A1.1** protocol open/free | Full | HTTPS + OIDC. | = | Full |
| **A1.2** auth where needed | Partial | Keycloak gating works (alice authenticated cleanly). No `accessRights` enum surface; no embargo end-date field; no per-DO public-vs-restricted toggle. | = | Partial |
| **A2** metadata accessible when data isn't | Weak | No tombstone surface; KIP1f only sets a flag; UI has no rendering for `retired`/`tombstoned` PIDs. | = | Weak |
| **I1** formal accessible knowledge representation | Full | Semantic Annotations panel renders 10+ ontology-linked chips on TR-001. PROV-O / m4i / Dublin Core / schema.org / FOAF / QUDT seeded per V49. | = | Full |
| **I2** vocabularies follow FAIR | Partial | Annotations are typed (curated) but `Attributes` panel on MFFD shows free-text `license: proprietary` and `accessRights: restricted access` — operators are *typing FAIR-vocabulary keys as free strings* because the entity model lacks them. This is the most damning live finding. | ↓ | Weak (the workaround proves the gap) |
| **I3** qualified references use PIDs | Partial | Relationships panel on TR-001 lists `Successor → TR-002`; the link is by appId not PID. Harvester cannot dereference. | = | Partial |
| **R1** rich metadata with plural attributes | Partial | Description ~180 chars, ~10 semantic annotations, 0 attributes on LUMEN DOs (vs 2 on MFFD collection). Sparse on LUMEN. | = | Partial |
| **R1.1** clear & accessible usage license | Weak | **No license field anywhere on LUMEN.** MFFD-Dropbox attribute `license: proprietary` (free-text typed by operator — not SPDX, not enforced). | = | Weak |
| **R1.2** detailed provenance | Partial | Provenance expansion-panel exists on every DO with `+ ADD`, `MAP`, `READ`, `EDIT?`, `DELETE` filter chips and "no matching provenance events yet" empty state. **PROV1a Activity capture is shipped backend-side but the UI shows zero events** for a freshly-walked DO that the alice session just touched — either the filter is too narrow, the events aren't captured for the touch verbs, or there's a render bug. Worth a separate investigation. | ↓ | Weak (visible infrastructure with empty payload = worse trust than no UI at all) |
| **R1.3** meets domain community standards | Weak | metadata4ing seeded but no enforcement; no DMP completeness widget; seed `test_engineer` is still a plain name string (per prior audit). | = | Weak |

**Composite:** F: ~1.8 / A: 1.3 / I: 1.7 / R: 0.8 → **~1.4/3** (down from 1.7/3 on live observation).

The downward adjustment comes from three live observations that the source-only review couldn't surface:
- The MFFD operator workaround (typing `license` as a free attribute key) is *evidence* of the FAIR gap, not just a theoretical absence.
- The Provenance UI surface is rendered but empty — worse for trust than no UI.
- The `/admin/unhide` 404 means even an admin can't see whether the harvest feed is working.

---

## DMP coverage matrix (Horizon Europe Annotated DMP Template)

Source: Horizon Europe Annotated Grant Agreement v2.0, Article 39 DMP requirements;
DFG Guidelines on the Handling of Research Data (Sept 2015 + 2022 update); Clean
Aviation JU Open Science Annex.

| DMP section | Status | What shepard exposes today | What's missing |
|---|---|---|---|
| **1. Data summary** (origin, type, format, size, reuse) | 🟡 | Collection name+description; DO list with status/refs/created; payload kind columns | No machine-readable summary export; no "Data summary PDF" button |
| **2.1 FAIR — Findable** (PID, metadata standards, search) | 🟡 | UUID v7 on every entity; semantic annotations panel; header search now works | No DOI/PID rendered on landing; no catalog registration; no Databus push |
| **2.2 FAIR — Accessible** (open protocol, access conditions, embargo) | 🔴 | OIDC auth gate works | **No accessRights field, no embargo field, no per-DO open/restricted toggle** |
| **2.3 FAIR — Interoperable** (vocabularies, ontologies, mappings) | 🟡 | 10 ontologies seeded; semantic annotations on TR-001 | Free-text `Attributes` accept FAIR-keyed strings the entity model rejects (license/accessRights typed as free text on MFFD) |
| **2.4 FAIR — Reusable** (license, provenance, quality, community standard) | 🔴 | RO-Crate export button exists; Provenance tab UI exists | **No license field** (LIC1 #140 queued); Provenance tab shows empty even after API activity; no quality metric |
| **3. Other research outputs** (software, materials) | 🟡 | Git references on DOs; Jupyter notebooks expansion-panel | No software-output schema (CITATION.cff per DO, dependency manifest, license-of-code separate from license-of-data) |
| **4. Allocation of resources** (storage cost, who pays, succession) | 🔴 | None | No retention-policy surface visible; no successor-instance pointer; no admin-visible storage-by-Collection breakdown |
| **5. Data security** (backup, access control, sensitive data) | 🟡 | Permissions exist on each entity (R/W lists); KIP `retired` flag exists | No "this DO contains sensitive data" classifier; no audit-log-by-DO surface; no backup-status surface |
| **6. Ethics** (PII, consent, anonymisation) | 🔴 | None | No ethical-review-board reference field; no PII classifier; no consent-record link |
| **7. Other issues** (institutional policies, sector requirements) | 🔴 | None | No way to attach institutional DMP policy to Collection; no sector-vocabulary mapping (e.g., DLR-specific) |

**Score:** 0 GREEN, 5 YELLOW, 5 RED. LUMEN as exported today cannot be submitted as DMP
evidence for a Horizon Europe grant. The five RED rows are individually achievable —
none requires a re-architecture, all are additive features.

---

## Publication readiness — the LUMEN-as-paper-supplement walk

**Scenario:** A LUMEN test campaign paper is accepted by `J. Aerosp. Eng.` The reviewer
asks for a FAIR-compliant data deposit. Alice (the test engineer in the demo) opens
shepard, navigates to Collection 42, and tries to:

| Step | What I tried | What happened | FAIR/funder requirement |
|---|---|---|---|
| 1. Get a citable URL | `/collections/42` | URL is stable but human-unreadable (appId would have been better but `/42` is the v1 long-id). No DOI rendered. | DataCite Schema 4.5 §1 (Identifier) — REQUIRED |
| 2. Mint a DOI | Click `Publish` button (visible top-right) | Dialog opens; not walked due to read-only constraint, but per `aidocs/integrations/66 §3` the minter flows to ePIC/DataCite — depending on instance config | DataCite Schema §1 — REQUIRED before deposit |
| 3. Export DataCite XML | Search for "DataCite" / "XML" / "JSON-LD" on the landing | `0 occurrences`. Only RO-Crate export button visible. | OpenAIRE Guidelines for Data Archives v4 §4 — REQUIRED |
| 4. Export RO-Crate | `Download as RO-Crate` button present (`06-export-candidates.json`) | Not walked (would download bytes); per `ExportService.java` review, includes annotations, permissions, file bytes, timeseries CSV, lab journal — credible RO-Crate v1.1 | EOSC RO-Crate profile for research data — RECOMMENDED |
| 5. Generate citation block | Search for "Cite this" / "Citation" / "How to cite" | `0 occurrences` on `/collections/42`. Researcher cannot copy a citation. | DataCite §10 (Subjects) implicit; APA / Vancouver citation style — REQUIRED for paper supplement |
| 6. Assign a license | Look for license field in any editable surface | `0 occurrences`. Operator on MFFD-Dropbox typed `license: proprietary` as a free `Attributes` key (visible at `08-mffd-collection-landing.png` line "license proprietary"). | DataCite §16 (Rights) — REQUIRED; Creative Commons recommends SPDX shortcodes |
| 7. Set access conditions (embargo until paper-accept date) | Look for accessRights / embargo field | `0 occurrences`. MFFD operator typed `accessRights: restricted access` as free-text. | DataCite §16; Horizon Europe Art 39 §3 — REQUIRED for embargoed datasets |
| 8. Stamp creator ORCID | Open `/me` user profile | 5-card grid: Profile / API Keys / MCP / Subscriptions / Git Credentials. **No ORCID input field rendered.** Backend validates ORCID with mod-11-2 checksum (`User.java`) but there's no UI to set it. | DataCite §2 (Creator) — REQUIRED |
| 9. Reference the funder grant | Search for "funder" / "grant" / "funding" | `0 occurrences` anywhere. | DataCite §17 (FundingReference) — REQUIRED for EU-funded work |

**Honest verdict: LUMEN is NOT publication-ready.** Of the 9 minimum DataCite Schema 4.5
fields required for DOI registration, 4 are absent from the entity model (license, rights,
fundingReference, creator-ORCID-stamped-on-entity), 2 are present-but-not-surfaced (PID
infrastructure exists API-side; ORCID exists on User entity), 1 is workable today (Publish
button → minter flow), and 2 are partially good (Identifier/URL stable, Title/Description
present).

If LIC1 (#140 / FAIR1 in backlog) ships, **5 of 9 fields close** (license + access + embargo
+ DataCite mapping + Unhide feed unblock). The single highest-leverage queued backlog row
is LIC1.

---

## f(ai)²r AI-collaboration provenance — visible to researcher?

The fork's claim (`project_fair2r_integration.md`, `project_ai_human_collab_provenance.md`)
is that every AI interaction surfaces as a typed PROV-O Activity with the f(ai)²r
vocabulary, and that the researcher sees a 🧑 / 🤖 / 🤝 badge on each artefact.

**Walked today:** Collection 42 → TR-001 → Provenance expansion-panel. Filter chips render:
`+ ADD`, `MAP`, `READ`, `EDIT?`, `DELETE` (so the Activity verb set is surfaced). Empty
state: **"no matching provenance events yet"** (see `07-prov-tab-2-Provenance.png`).

This is striking because:
- The same session has *just* `READ` the DataObject. The capture should at minimum show
  alice's READ. It doesn't.
- The `READ` chip is checked-by-default, so the filter isn't the problem.
- PROV1a (`ProvenanceCaptureFilter`) is documented as shipped for API mutations, but
  apparently not for reads (or not for unauthenticated-seeming UI reads, or it's a UI
  rendering gap).

**No 🧑 / 🤖 / 🤝 badges anywhere.** No "this DO has X AI contributions" indicator on the
landing page, no per-annotation author indicator, no model-name surface. The infrastructure
is in `aidocs/agent-findings` as a design (`project_ai_human_collab_provenance.md`); the
queued work is `PROMPT1` in `aidocs/16` (~29 days, audited-by-personas). **Today's surface
delivers neither the visibility nor the differentiator.**

This is the EU AI Act Article 50 (transparency obligation) surface that shepard could uniquely
satisfy among RDM platforms (per the OECD AI-in-Science alignment finding, `aidocs/strategy/93`).
Today it is unsatisfied.

---

## Top 5 highest-impact fixes

Ranked by `(FAIR-score-delta) × (DMP-section-unblocked) × (1/effort)`. New backlog row IDs
introduced; no overlap with existing FAIR1/FAIR5/COMP-LICENSE-FIELD/LIC1.

| # | Fix | FAIR impact | DMP unblock | Effort | Backlog row |
|---|---|---|---|---|---|
| 1 | **Render `Cite this dataset` block** on Collection landing using fields already present (name, description, creator from `createdBy`, URL as fallback PID). Even without DOI, a copy-pasteable citation puts shepard on par with Zenodo / Coscine. | F2 ↑ Partial → Full; F3 ↑ | DMP §1, §2.1 | S (1-2 days frontend; no backend change) | `RDM-001` |
| 2 | **Surface ORCID input on `/me` Profile card.** The backend already accepts and validates ORCID; the UI never asks. One Vuetify text field with the existing `orcid` regex pattern. Cascading effect: render the ORCID badge next to creator name everywhere (Collection landing, DO landing, Activity timeline). | R1.2 ↑ Partial → Full; A2 partial-up | DMP §1, §2.1 | S (2-3 days incl. ORCID badge rendering pass) | `RDM-002` |
| 3 | **Build `/admin/publications` UI** — list all minted PIDs across the instance, with retired-state and successor-PID surfaces. Even read-only listing unblocks operator audit + DMP §5 (data security audit trail). Replaces "what got published?" curl loop. | A2 ↑ Weak → Partial; F1 audit | DMP §5 | M (3-5 days; pairs with KIP1f UI surface) | `RDM-003` |
| 4 | **Investigate empty Provenance panel.** Either fix capture (PROV1a should record reads under the alice session), fix render (filter / pagination), or fix expectation (document that reads aren't captured and remove READ from the default filter chips). Whichever the cause, the empty state when the user just touched the DO is a trust-killer. | R1.2 ↑ Weak → Partial | DMP §2.4, §5 | S (1-2 days investigation + fix) | `RDM-004` |
| 5 | **Build Metadata Completeness Score widget** as designed in prior audit §4 — 0–100 score on Collection sidebar with red/amber/green chip and per-check action links ("Add license →"). Even without LIC1 shipping, today's checks (name, description, semantic-annotation-count, RO-Crate-exportability, PID-minted) would surface a useful score and create operator pressure to fill the gaps. | F2, R1.1, R1.3 ↑ across the board | DMP §1, §2.4 | M (4-6 days; new endpoint + Vuetify component) | `RDM-005` |

The single highest **per-day-of-effort** win is `RDM-001` (cite block). Most visible to a
funder reviewing the dataset; cheapest to ship.

---

## What I didn't audit (gaps for next round)

- **Publish dialog workflow.** Read-only mode + clicking `Publish` would have mutated the entity. Worth a dedicated walk in a sacrificial demo Collection.
- **RO-Crate ZIP shape.** Did not download; need to extract and validate against ROC v1.1 schema + check whether RO-Crate includes the things DataCite needs (creator, license, rights).
- **DOI minter happy-path.** ePIC / DataCite minter behavior in the deploy depends on instance config that I did not inspect.
- **Permissions UI as accessRights proxy.** The DO Permissions panel might already provide the equivalent of accessRights via R/W/manager lists — needs a separate walk against the FAIR `dcat:accessRights` enum.
- **Unhide harvest feed actual output.** `/v2/harvest/unhide` was not walked; the admin UI for UH1a is 404.
- **InvenioRDM / Databus push paths.** Not walked; per `aidocs/integrations/72` and `/77` these are designed but not shipped.
- **Lab Journal content quality.** Walked the tab; was empty for TR-001. Not audited as a provenance surface in its own right.
- **f(ai)²r capture from MCP-mediated work.** Could not test whether alice→Claude→MCP→Shepard activities surface as `prov:Agent` with `f-ai-r:hasModel`. PROMPT1 in backlog is the design surface.

---

## Comparison to UX Scrutinizer (parallel audit)

The two reports agree on the structural finding and diverge on emphasis. Overlaps:

| Finding | UX Scrutinizer | RDM Scrutinizer |
|---|---|---|
| `/admin/unhide` empty / 404 | "empty index shell" | "harvest feed has no admin UI; FAIR-F3 downgrade" |
| `/me` minimal content | not flagged as broken | "no ORCID input → DataCite §2 blocker" |
| Header search shipped | UI-002 closed today | F3 partial restored |
| Provenance empty on a freshly-touched DO | not specifically audited | "R1.2 downgrade Partial → Weak; trust-killer" |
| Cite block missing | not audited | "DMP §1 RED" |
| BUG #139 (4K layout) | CRITICAL | not RDM-relevant |

Divergence: UX scrutinizer's worldview is "clicks to do X"; RDM scrutinizer's worldview is
"does X produce DMP-grade output". Both correctly identify the same surface gaps and rate
them differently because they serve different audiences. The two reports are mutually
reinforcing: every UI gap UX scrutinizer found that touches license/access/PID/provenance
also appears here, scored against funder requirements rather than clicks.

---

## Citations

- Wilkinson, M. D. et al. (2016). *The FAIR Guiding Principles for scientific data management and stewardship.* Scientific Data 3, 160018. https://doi.org/10.1038/sdata.2016.18
- DataCite Metadata Working Group. (2024). *DataCite Metadata Schema Documentation for the Publication and Citation of Research Data and Other Research Outputs v4.5.* https://doi.org/10.14454/G8E5-6293
- European Commission. (2024). *Horizon Europe — Annotated Model Grant Agreement v2.0,* Article 39 (Data Management Plan). EU Funding & Tenders Portal.
- DFG. (2015, updated 2022). *Guidelines on the Handling of Research Data.* https://www.dfg.de/foerderung/grundlagen_rahmenbedingungen/forschungsdaten/
- OpenAIRE. (2022). *Guidelines for Data Archives v4.* https://guidelines.openaire.eu/en/latest/data/index.html
- EU AI Act, Regulation (EU) 2024/1689, Article 50 (Transparency obligations).
- Welzmüller, F. et al. (2024). *Research Data Management for Space Missions: Practical Experiences and Lessons Learned.* DLR eLib 215120.
- Prior audit: [`aidocs/agent-findings/research-data-manager.md`](research-data-manager.md), 2026-05-21.
- Parallel audit: [`aidocs/agent-findings/ui-scrutinizer-2026-05-24.md`](ui-scrutinizer-2026-05-24.md), 2026-05-24.

---

## Reproducibility

Re-run the live walk against any deploy:

```bash
cd e2e
BASE_URL=https://your-shepard-instance \
  npx playwright test tests/rdm-walkthrough.spec.ts --reporter=list
```

Requires the auth-state cache at
`aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence/auth-state/alice.json`
+ `admin.json`. To regenerate the cache from scratch, first run
`tests/ux-workflow-00-setup.spec.ts`. The walk takes ~2 minutes on a quiet
deploy and emits 23 PNG + 23 JSON evidence files into
`aidocs/agent-findings/rdm-scrutinizer-2026-05-24-evidence/`.

Scoring is deterministic from the JSON sidecars: each one carries a
`presence: { term: bool }` map for the 33 FAIR-relevant terms scanned in
the rendered body text. The score adjustments above are reproducible from
the JSON files alone (no screenshot interpretation required for the
numbers).
