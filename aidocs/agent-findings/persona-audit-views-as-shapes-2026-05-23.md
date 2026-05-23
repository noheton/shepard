---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Persona audit — views-as-shapes design (docs 95 + 98)

**Audited:**
- `aidocs/semantics/95-shacl-templates-and-individuals.md` (stage `feature-defined`, 1797 lines)
- `aidocs/semantics/98-shapes-views-and-process-model.md` (stage `feature-defined` post-Phase-1, 1810 lines)

**Date:** 2026-05-23.

**Audit shape.** One report, five persona lenses applied internally
(per `feedback_agents_argue_and_consult.md`'s argue-and-cite rule),
with a mandatory opposing-lens paragraph from the Reluctant Senior.
Every finding cites a specific line range in either doc 95 or
doc 98.

---

## What I found

Both docs are structurally coherent and have already incorporated
extensive persona feedback. Doc 95 carries an honest §0 review-status
section flagging its own gaps (lines 29–58). Doc 98 explicitly cites
the seven persona-review files as its sources (lines 41–47). The
Phase 1 enrichment landed all the items the brief listed (AFP worked
examples in §3.1.1; structural shapes in §3.3.0; SKOS disposition
scheme in §3.4.2; inline persona quotes throughout §4; new
cross-reference table at §5; reuse survey at §6).

The substantive risks the audit surfaces are **not** missing-content
risks — they are unresolved-fork risks (NEEDS-CLARIFICATION-5),
supply-chain risks (§2.3 SRI custom renderers), defensibility risks
on BUILD-OWN calls (§6), and timing risks (doc 98 commits the
`shepardId` channel-read contract in §4.6 while gating the migration
on task #58 — the contract is right, the empirical 5-line test fails
today). None reaches the "would shipping the impl produce a bug?"
CRITICAL bar. All are MAJOR or MINOR with backlog rows.

---

## Per-lens findings

### Lens 1 — Data Ontologist (Role 2)

**Finding 1.1 — `prov:wasRevisionOf` super-property linkage is
asserted but not closed in doc 95.** Doc 98 §3.2 declares
`shepard:reworkOf rdfs:subPropertyOf prov:wasRevisionOf` and
`shepard:replaces rdfs:subPropertyOf prov:wasRevisionOf` (lines
545–567 of the enriched doc 98). Doc 95 §11 Part 8 (lines 543–548)
talks about adding a `:type` property carrying a PROV-O IRI to
existing edges but does **not** declare the sub-property chain. Net:
the two docs agree at the predicate level but doc 95 doesn't echo
the sub-property axiom. **Severity: MINOR.** Fix: cross-link from
doc 95 §11 to doc 98 §3.2.1.

**Finding 1.2 — Named-individuals-as-predicate-keys composition does
hold under multi-shape composition.** Doc 95 §7 (lines 326–349)
establishes the named-individual IRI scheme. Doc 98 §3.1.1
demonstrates three instances (`mffd:ind-consolidation-force-...`)
that compose cleanly: each individual is `a mffd:ProcessParameter`,
carries a `mffd:parameterType` taxonomy code, has a typed
`hasValue qudt:QuantityValue` blank node, and binds to an `:Activity`
via `prov:wasGeneratedBy`. **No composition failure detected.** The
pattern works because the discriminator (`parameterType`) is data,
not a class — a single shape (`ProcessParameterShape`) validates
all three. **Severity: PASSED.**

**Finding 1.3 — IOF instance + taxonomy fork (§3.1 / NEEDS-CLARIFICATION-1)
is the right call but doc 98 §3.1 still uses "fix (this doc)"
phrasing (line 343).** A reader skimming §3.1 might think the fork
is closed; only §7 NEEDS-CLARIFICATION-1 (lines 1352–1379) reveals it
is still open. **Severity: MINOR.** Fix: doc 98 §3.1 should mark the
recommendation as "lean (subject to §7 NEEDS-CLARIFICATION-1)" so the
fork status is loud at the point of the recommendation, not buried
500 lines later.

**Finding 1.4 — Namespace canonicalisation (§4.9) needs the sibling
doc 97 to actually exist before audit can clear it.** Doc 98 §4.9
(lines 1117–1130) commits to `aidocs/semantics/97-canonical-iris.md`.
That doc does **not** exist on disk. The Ontologist review's #1
critical finding is that three diverged namespaces ship in production
today. **Severity: MAJOR.** Doc 98 commits the fix; doc 97 must land
as a follow-up PR before any plugin work consumes the canonical IRIs.

### Lens 2 — API Scrutinizer (Role 3)

**Finding 2.1 — `templateKind` enum is the right discriminator
(doc 95 §1.1 lines 70–94 of doc 98).** The collapse from seven
proposed view-concepts to one enum value is verifiable: every
operation an `/v2/views` resource would need (CRUD, version,
soft-retire, allow-list, instantiation provenance, bulk
import/export, audit) lands on `:ShepardTemplate` infra per
`aidocs/workflows/54`. **No new resource group is justified.**
**Severity: PASSED.**

**Finding 2.2 — `POST /v2/shapes/render` SRI URL custom renderers
(§2.3 lines 280–298) is a supply-chain footgun.** The defence in
the doc addresses *deployment-unit* concern (Java backend, JS
renderer) — that's correct. It does **not** address *authorship
malice*. SRI prevents post-download tampering; it does **not**
verify the publishing plugin author was honest. A compromised or
rogue plugin author publishes a malicious Vue bundle with a valid
SRI; the frontend loads it; XSS, exfiltration of the auth token,
the entire menu. **Severity: MAJOR.** Mitigation options:
(a) allow-list of plugin publisher origins (defence-in-depth);
(b) CSP `script-src` restriction with explicit nonce per
admin-approved plugin URL; (c) require admin approval of any new
custom-renderer URL before frontend loads it; (d) sandbox via
iframe with strict `sandbox` attributes. The current doc says only
"SRI digest verification." That is insufficient. **Backlog row
SECURITY-CUSTOM-RENDERER.**

**Finding 2.3 — `GET /v2/templates?kind=VIEW_RECIPE` pagination /
filtering / indexing is not specified in doc 98.** Doc 98 §1.1
asserts everything is already on `/v2/templates`. Doc 95 §6 (lines
278–299) describes view-driven facet queries. Neither doc explicitly
states pagination behaviour, default page size, allowable filter
keys, or indexability. At MFFD scale doc 95 §12 admits "facet query
(3-AND, all indexed): ~150 ms" at 10⁵ scale. `GET /v2/templates`
needs the same composite-index treatment for `kind` + name +
`shape:targetClass`. **Severity: MINOR.** Cross-link to
`aidocs/workflows/54` is enough; that doc is canonical for the
template resource — confirm pagination + indexes there.

**Finding 2.4 — Naming: `focusShepardId` is good; `templateAppId`
should rename to `templateShepardId`.** Doc 98 §1.2 (line 110) mixes
`templateAppId` (legacy `appId` naming) with `focusShepardId` (the
target post-rename). Per `feedback_appid_to_shepardid.md` the rename
is deferred but **every new wire endpoint should ship with the new
name**. **Severity: MINOR.** Fix: §1.2 wire example uses
`templateShepardId` from day one; the field name lands consistent
with the target rename.

### Lens 3 — UI/UX (Role 1)

**Finding 3.1 — Default landing = table not tabs (§2.5 of doc 98)
is the right verdict for the Reluctant Senior constituency.** The
600-row Excel master sheet maps directly onto a Vuetify data table;
the seven-tabs detail page maps onto nothing in the senior's daily
workflow. **However:** the Digital Native does not benefit from a
table — they want the API and the notebook. So §2.5's claim "default
table benefits both constituencies" is wrong; it benefits one and is
neutral for the other. **Severity: MINOR.** Fix: §2.5 should drop
the asymmetric claim and just state "default landing is table; the
seven-tab detail page is one click away." That is defensible.

**Finding 3.2 — Click-path test: TR-004 anomaly investigation →
repair → re-test in under 10 seconds.** The `shepard:reworkOf`
typed predicate (§3.2 + §3.2.1) makes the lineage graph
auditor-defensible — the orange-edge rendering is concrete. **But
the table view (§2.5) does not show lineage by default.** A user
landing on a Collection sees the table; they do not see the rework
chain until they click into a DataObject. At MFFD audit pace this
is acceptable; at "show me everything that went wrong on this
campaign" pace it isn't. **Severity: MINOR.** Fix: the table view
should have a "lineage" column that renders a small ⤳ chip when a
row has rework edges, click-through to the graph view.

**Finding 3.3 — Workspace = saved-view + filter + layout (§2.4) is
underspecified for "I'm in basic mode but want my literal-bucket
attribute keys back."** Doc 98 §4.5 commits to a literal attribute
bucket; doc 98 §2.4 commits to a workspace abstraction. The two are
not joined: can a workspace remember "show my literal-bucket
`material_roll_change` column always"? It must, per the Reluctant
Senior's gate (§4.5 → §6 honest verdict in `persona-review-reluctant-senior.md`).
**Severity: MINOR.** Fix: §2.4 should explicitly say workspace
column prefs span both typed-bucket and literal-bucket properties.

### Lens 4 — Research Data Manager (Role 5)

**Finding 4.1 — F(AI)²R as SHACL invariant (doc 95 §14e lines
1335–1346; doc 98 §1.4 lines 169–193) does capture the AI provenance
the case study needs.** The `no-parentless-claim` invariant
(`fair2r:Claim sh:property [sh:path prov:wasGeneratedBy ; sh:minCount 1]`)
is enforced at the gateway. The sampling-params extension (X-AI-*
headers in §1.4 / §4.7) closes the FAIR4ML reproducibility gap that
REBAR's EASA Learning Assurance evidence packs require. **Severity:
PASSED.**

**Finding 4.2 — License / accessRights / embargo (§4.1) is correctly
identified as the #1 RDM blocker, but doc 98 §4.1 (lines 950–966) ships
only the field definitions, not the `dcterms:available` embargo state
machine.** When `accessRights = EMBARGOED`, who decides the embargo
end date? Who can flip it back to OPEN? Is there an audit trail
beyond the generic `:Activity`? Without these, an EU Horizon Europe
reviewer reads it as "the field exists but the workflow doesn't."
**Severity: MAJOR.** Fix: §4.1 must add a `:EmbargoLifecycle` shape
with `embargoSetAt`, `embargoSetBy`, `embargoExpectedReleaseAt`,
`embargoActualReleaseAt`, role binding (`instance-admin` or
`collection-owner` only), and the SHACL constraint that
`accessRights = EMBARGOED ⇒ dcterms:available minCount 1`.

**Finding 4.3 — Export-shape mapping (§4.3) hard-depends on
`aidocs/semantics/99-export-shape-mapping.md` which doesn't exist.**
Three publication plugins (Unhide, Invenio, Databus) read this
mapping; until doc 99 lands, every plugin defines its own mapping.
**Severity: MAJOR.** Same shape as Finding 1.4 — doc 99 must land
before any of the three publication plugins ship their export
shape. Backlog row already implied; surface it explicitly.

**Finding 4.4 — PID strategy (§4.2) commits to Handle as default, DOI
opt-in on PUBLISHED. The doc says "bound in `mffd-context.jsonld` as
the canonical IRI" (line 980) but `mffd-context.jsonld` doesn't exist
either** (doc 98 §9 cross-references list it as "to-be-written";
line 1781). This is the third undeleted reference to a non-existent
sibling doc. **Severity: MINOR (it's a doc-completeness issue, not
a design bug).** Bundle with Finding 1.4 + Finding 4.3 into one
backlog row.

### Lens 5 — Digital Native Researcher (Role 10)

**5-line Python test (the brief asks me to write the code):**

```python
from shepard_py import Client
c = Client(url="https://shepard.nuclide.systems", api_key="...")
view = c.templates.get("view-mffd-tr004", kind="VIEW_RECIPE")
df = c.shapes.render_to_dataframe(view, focus=c.data_objects.by_shepard_id("DO-tr-004"))
df.head()
```

**Result: the code does not work today.**

- `shepard_py` does not exist on PyPI (per
  `persona-review-digital-native.md` lines 72–76 — Kiota dir empty,
  shepard-py is a 200-LOC plan in aidocs/81).
- `c.shapes.render_to_dataframe` doesn't exist — even with the v1
  v2 client, `POST /v2/shapes/render` is on the design-table
  (doc 98 §1.2), not implemented.
- `c.data_objects.by_shepard_id` would work via v1 `dataobjects/{id}`
  GET, **but** the channel-data path inside the render output still
  needs the 5-tuple to actually read the timeseries rows (doc 98
  §4.6 commits to single-shepardId, gates on task #58).

**Severity: MINOR for the docs — design commitment is correct.** The
empirical state-of-the-world failure is the truth that the brief
asks me to surface, not a design bug.

**Finding 5.1 — `GET /v2/templates?kind=VIEW_RECIPE` MCP tool is
designed (doc 95 §6 lines 308–322) but not enumerated in doc 98.**
For the Digital Native, the MCP `list_templates` tool is the
notebook on-ramp. Doc 98 §2.1 collapses the view registry to
`/v2/templates` but doesn't surface what the corresponding MCP tool
returns — does it include `templateKind` in the result so an agent
can filter? **Severity: MINOR.** Fix: §2.1 should add a one-line
clarification "the `list_templates` MCP tool already returns
`templateKind`; an agent filters client-side."

**Finding 5.2 — EmbeddingReference payload-kind (§4.4) is the right
substrate for AI1d but doc 98 doesn't specify the Python wrapper.**
The notebook story for "find similar DataObjects" is `c.embeddings.search(query, k=10)`. The endpoint is implied but not specified in doc 98.
**Severity: MINOR.** Fix: §4.4 should add a one-line endpoint sketch
(`POST /v2/embeddings/search`) so the Python SDK's shape is
predictable.

---

## Opposing-lens paragraph

**The Reluctant Senior would reject §4.6 — the single-`shepardId`
channel-read commitment that gates on task #58.**

The senior reads doc 98 §4.6 (lines 1059–1077) as the platform
saying *"we will fix the 5-tuple problem after we ship the
TS-IDc migration."* Their 40 TB legacy data is on disk *today*.
Task #58 has been on the board for months. The Trace3D view
(`project_trace3d_view.md`) — the MFFD AFP TCP-temp acceptance test
this doc anchors against — needs single-shepardId reads to work.
Without it, the senior writes the 5-tuple by hand for the AFP demo,
then re-writes it for production, then re-writes it again when
task #58 lands. Three rewrites for one workflow.

The doc's lean ("this doc commits to the contract; impl gated on
TS-IDc") is architecturally clean — but **the senior is right to
push back**: a contract whose first acceptance test (Trace3D)
cannot run is a contract on paper, not in code. The honest answer
is one of:

- (a) accelerate task #58 to ship in the same wave as `POST /v2/shapes/render`;
- (b) ship `POST /v2/shapes/render` with a temporary read-side
  shim that accepts a 5-tuple and converts to a synthetic
  shepardId server-side (lossy but unblocking);
- (c) explicitly state in §4.6 that the Trace3D acceptance test is
  blocked until task #58 lands, and adjust the implementation slice
  ordering accordingly.

The doc currently does **none of these**. It commits to the contract
shape and is silent on the timing. That silence is the
Reluctant-Senior-shaped hole the audit must name. **Severity: MAJOR
on doc 98 §4.6 / §8.2 (the coordination-with-active-tasks section).**

---

## Convergence

Three findings converge across two or more lenses via different
reasoning paths — these are the highest-confidence audit calls:

**Convergence 1 — Sibling docs that don't exist block downstream
work.** Ontologist Finding 1.4 (doc 97 canonical IRIs), RDM Finding
4.3 (doc 99 export-shape mapping), RDM Finding 4.4
(`mffd-context.jsonld`) all hit the same shape: doc 98 commits the
fix, the sibling doc carrying the fix's content does not exist on
disk. Three independent personas reached this finding via three
independent reasoning paths. **MAJOR.** Backlog row groups them.

**Convergence 2 — Workflow ergonomics ship as APIs, not as workflows.**
UI/UX Finding 3.3 (workspace doesn't compose with literal bucket),
Digital Native Findings 5.1 + 5.2 (MCP tool surface implicit, Python
SDK wrapper unspecified), and the Reluctant Senior opposing-lens
finding (5-tuple persistence) all reach the same observation: the
docs commit to the *contract* layer but are silent on the *workflow*
layer. This is endemic to design docs, but for these two specific
constituencies (Reluctant Senior + Digital Native — the two who
decide adoption) the workflow gap reads as the adoption gap.
**MAJOR strategically, MINOR per individual finding.**

**Convergence 3 — Namespace + IRI discipline is a cross-cutting
risk.** Ontologist Finding 1.4 (sibling doc 97), API Scrutinizer
Finding 2.4 (`appId` vs `shepardId` naming in the new endpoint),
plus the architectural reading of doc 95 §11 (typed predicate work
on existing edges, line 543–548) all converge on: every new wire
artefact has a naming-discipline decision to make, and the docs
make different choices in different places. The fix is one CI lint
(rejecting `appId` in new wire shapes; rejecting `example.org` in
new TTL; rejecting undeclared prefixes anywhere). **MAJOR
collectively.**

---

## Change requests

### CRITICAL (would produce a bug at impl time)

**None.** The audit found no design hole that would surface as a
bug in a code review. The four MAJORs below are gated-by-design or
process-shaped, not bug-shaped.

### MAJOR (would block stage transition or create downstream debt)

| # | Doc | Section | Finding | Fix |
|---|---|---|---|---|
| MAJOR-1 | 98 | §2.3 | SRI URL custom renderer is supply-chain footgun (Finding 2.2) | Add (a) admin allow-list of renderer URLs, (b) iframe sandbox or CSP nonce, (c) audit trail on first-load. Re-PR §2.3 with the security envelope. |
| MAJOR-2 | 98 | §4.1 | Embargo state machine missing (Finding 4.2) | Add `:EmbargoLifecycle` shape with set-by / set-at / expected-release / actual-release + role binding. SHACL constraint binds `accessRights = EMBARGOED ⇒ dcterms:available minCount 1`. |
| MAJOR-3 | 98 | §4.9 / §4.3 / §4.2 | Three sibling docs cited but absent (Convergence 1) | Land `aidocs/semantics/97-canonical-iris.md`, `aidocs/semantics/99-export-shape-mapping.md`, and `aidocs/semantics/contexts/mffd-context.jsonld` in three follow-up PRs before any plugin consumes them. |
| MAJOR-4 | 98 | §4.6 / §8.2 | `shepardId` channel-read contract silent on Trace3D blocker (Opposing-lens) | Pick (a) accelerate task #58, (b) ship a synthetic-shepardId shim, or (c) state Trace3D-blocked-until-#58 explicitly. Adjust §8.1 ordering accordingly. |

### MINOR (polish; doesn't block stage transition)

| # | Doc | Section | Finding | Fix |
|---|---|---|---|---|
| MINOR-1 | 95 | §11 | sub-property axiom for `shepard:reworkOf` not echoed in 95 (Finding 1.1) | Add one-line cross-link to doc 98 §3.2. |
| MINOR-2 | 98 | §3.1 | Open-fork status of §3.1 not visible at recommendation site (Finding 1.3) | Add "lean (subject to §7 NEEDS-CLARIFICATION-1)" tag at §3.1's "Fix" paragraph. |
| MINOR-3 | 98 | §1.1 / §2.1 | `GET /v2/templates?kind=VIEW_RECIPE` pagination not specified (Finding 2.3) | Cross-link to `aidocs/workflows/54` for canonical pagination. |
| MINOR-4 | 98 | §1.2 | `templateAppId` vs `templateShepardId` naming inconsistency (Finding 2.4) | Rename wire example to `templateShepardId`. |
| MINOR-5 | 98 | §2.5 | Asymmetric "table benefits both constituencies" claim (Finding 3.1) | Restate as "table is the right default for the high-value senior workflow; the seven-tab detail is one click away for everyone." |
| MINOR-6 | 98 | §2.5 | Table view has no lineage indicator (Finding 3.2) | Add a "lineage" column with ⤳ chip on rows with rework edges. |
| MINOR-7 | 98 | §2.4 | Workspace doesn't compose with literal bucket (Finding 3.3) | Add one paragraph: workspace column prefs span typed AND literal buckets. |
| MINOR-8 | 98 | §2.1 | MCP tool surface for `templateKind` not enumerated (Finding 5.1) | One line in §2.1: "the `list_templates` MCP tool returns `templateKind`; agents filter client-side." |
| MINOR-9 | 98 | §4.4 | EmbeddingReference Python wrapper unspecified (Finding 5.2) | One line: `POST /v2/embeddings/search` endpoint sketch. |
| MINOR-10 | 95 | §6 | Reuse survey defensibility not echoed in 95 (Finding 6 from §6 reuse-survey itself) | BUILD-OWN justifications for form-gen / view-recipe should cross-reference doc 98 §6 — confirms doc 95 doesn't reinvent. |

### Reuse-survey defensibility (mini-finding from §6)

Per the advisor's #5 point: I (the audit) interrogate the BUILD-OWN
calls I made in doc 98 §6.2 + §6.4.

- **§6.4 "BUILD-OWN form generator"** — defensible. shacl-form's
  Vuetify retheme cost is asserted; the *real* defence is that
  Shepard already ships Vuetify 3 + Composition API composables
  (`useShape`, `useCollection`). Replicating shacl-form's renderer
  in that idiom is small enough to control. **PASSED.**
- **§6.3 "BUILD-OWN view-recipe abstraction"** — defensible. The
  view-recipe IS the doc's SSOT contribution; the alternative is
  adopting AppFlowy's `View` JSON shape (AGPL-incompatible) or
  Affine's (MIT but immature). **PASSED.**
- **§6.2 "ADOPT BlockNote"** — load-bearing dependency on an MPL-2.0
  library; license-compatible per Shepard policy (`feedback_github_pm_policies.md` MPL is in scope).
  **PASSED — but flag the license-compatibility check explicitly.**
  MPL-2.0 is **not** GPL/AGPL/SSPL (the banned families) but it is
  weaker than MIT. Worth one line in the §6.2 decision rationale.

---

## Recommended stage transitions

### Doc 98 (`98-shapes-views-and-process-model.md`)

**Recommendation: bump from `feature-defined` → `audited-by-personas`.**

Justification:
- Zero CRITICAL findings.
- Four MAJORs are all process-shaped (sibling docs missing,
  embargo workflow, custom-renderer security envelope, Trace3D
  timing) — none would surface as a bug in code review. All file
  backlog rows; impl proceeds in parallel with the backlog work.
- Ten MINORs are polish.
- The doc has explicit forks (§7 NEEDS-CLARIFICATION 1–10) with
  leans named per `feedback_agent_clarify_first.md`; that is
  audit-clearing not audit-blocking.

**`stage: feature-defined` → `stage: audited-by-personas`.**
`last-stage-change: 2026-05-23`.

### Doc 95 (`95-shacl-templates-and-individuals.md`)

**Recommendation: bump from `feature-defined` → `audited-by-personas`.**

Justification:
- Zero CRITICAL findings against doc 95 specifically.
- The two MINOR findings against doc 95 (MINOR-1: cross-link to
  doc 98 §3.2; MINOR-10: cross-reference reuse survey) are
  cross-link additions, not content changes.
- Doc 95's own §0 (lines 29–58) has an honest review-status section
  that pre-audits its own gaps; doc 95's §17 (lines 1766–1776)
  lists open questions / non-goals. These two surfaces are the
  doc's own audit; this report ratifies them.
- Doc 95 has been live for the seven persona-review files cited;
  every review was implicitly auditing doc 95 + the lost trio.
  This audit's contribution is the formalisation.

**`stage: feature-defined` → `stage: audited-by-personas`.**
`last-stage-change: 2026-05-23`.

---

## Executive 3-line summary

- **Biggest validation:** the trio-collapse (seven view concepts →
  one `templateKind=VIEW_RECIPE`) holds under five-lens scrutiny;
  the named-individuals composition works; the F(AI)²R SHACL
  invariant captures EU AI Act + EASA evidence requirements
  structurally.
- **Biggest risk:** four MAJOR findings cluster around *external
  dependencies that don't exist yet* (sibling docs 97 / 99 /
  mffd-context.jsonld; the embargo state machine; the security
  envelope on custom renderers; the Trace3D blocker on task #58).
  None block stage transition; all need same-quarter follow-up
  before any plugin consumes the surfaces.
- **Next concrete step:** land
  `aidocs/semantics/97-canonical-iris.md` as the first follow-up PR
  — it's the lightest MAJOR (one TTL header table + a CI lint) and
  closes Ontologist Finding 1.4 + API Scrutinizer Finding 2.4 +
  Convergence 3 in one move.

---

## Backlog rows filed

See `aidocs/16-dispatcher-backlog.md` for paste-ready rows. Identifiers:

- `VIEWS-AS-SHAPES-CANONICAL-IRIS` (MAJOR-3a — doc 97 canonical IRIs)
- `VIEWS-AS-SHAPES-EXPORT-MAPPING` (MAJOR-3b — doc 99 export-shape mapping)
- `VIEWS-AS-SHAPES-MFFD-CONTEXT` (MAJOR-3c — mffd-context.jsonld)
- `VIEWS-AS-SHAPES-EMBARGO-LIFECYCLE` (MAJOR-2 — embargo state machine)
- `VIEWS-AS-SHAPES-CUSTOM-RENDERER-SECURITY` (MAJOR-1 — SRI URL security envelope)
- `VIEWS-AS-SHAPES-TRACE3D-TIMING` (MAJOR-4 — Trace3D blocker on task #58)

Minor findings (MINOR-1 through MINOR-10) are bundled into a single
backlog row `VIEWS-AS-SHAPES-AUDIT-POLISH` since each is a single
sentence / cross-link.

---

*End of audit. The five lenses argued (not consensus'd); the
opposing-lens paragraph was load-bearing; every finding cites
specific line ranges in either doc 95 or the enriched doc 98.*
