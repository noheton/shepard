---
stage: idea
last-stage-change: 2026-05-29
audience: contributors + operator
---

# 110 — Permissions: redesign or extend in place?

**Snapshot date:** 2026-05-29
**Status:** decision doc — no closing answer. Operator + persona consult required.
**Parents:** [`24-permission-system-review.md`](24-permission-system-review.md)
(walk algorithm, fragility ranking), [`../data/PERM-INHERIT-MATRIX.md`](../data/PERM-INHERIT-MATRIX.md)
(entity → perm source matrix).
**Backlog:** PERM-REDESIGN-DECISION (this doc); gates PERM-INHERIT-03.

---

## 1. Problem statement

The 2026-05-29 microsections-grant exercise made three forces visible at once.
**First**, a single Collection-level grant silently misses the top-level
container payloads (`FileContainer`, `TimeseriesContainer`,
`StructuredDataContainer`, `HdfContainer`) that LUMEN- and MFFD-style
showcases depend on — the cascade-grant footgun documented in
[`PERM-INHERIT-MATRIX §3`](../data/PERM-INHERIT-MATRIX.md#3-the-operator-footgun-containers-escape-the-cascade).
**Second**, the dual ownership model is a deliberate structural choice, not
an accident: containers own their own `:Permissions` *because* they are
multi-attachable across Collections (one container, many parents — perm
inheritance through a single parent is structurally impossible without
losing that capability). **Third**, the BUG-148 anti-regression
([`PERM-INHERIT-MATRIX §4`](../data/PERM-INHERIT-MATRIX.md#4-bug-148-reconcile--xs-perm-inherit-04))
locks in the converse invariant for `DataObject` and `*Reference` — they
must **not** acquire their own `:Permissions`, or the inheritance walk
short-circuits silently. Together these three pin the model in place at
the *current* shape: any redesign must respect multi-attachment, respect
the BUG-148 lock, and (separately) close the cascade footgun for operators.

The operator question now is: **does PERM-INHERIT-03 — a cascade endpoint —
ship as the right shape, or does it ship as the local fix while we commit
to a broader redesign?**

---

## 2. Current shape (recap)

**Matrix.** ~40 entity kinds catalogued in [`PERM-INHERIT-MATRIX §2`](../data/PERM-INHERIT-MATRIX.md#2-the-matrix).
Six "own" perms: `Collection`, the four container kinds, `UserGroup`.
Everything else either inherits via a parent walk that bottoms out at a
`:Collection` (DataObject and all `*Reference` kinds), inherits to a
container (`ShepardFile`, `StructuredData`, `PayloadVersion`,
`TimeseriesContainerChartView`), or sits outside the discretionary-access
perimeter (admin-gated singletons, identity infrastructure, system-owned
provenance).

**Walk.** Codified in [`24 §9`](24-permission-system-review.md#9-perm-inheritance-walk-algorithm-per-entity-kind):
two entry points (`PermissionsService.isAccessTypeAllowedForUser`
3-arg overload; `GraphPolicyDecisionPoint.isAllowed` appId-keyed F6 seam)
converge on the same algorithm — direct `:Permissions` hit wins,
otherwise one-hop Cypher to the parent `:Collection`. Five short-circuit
invariants, all fail-closed (Neo4j down, unknown entity, orphan,
missing `:Permissions` post-C3, direct-hit-on-anti-regression-label).
A `PolicyDecisionPoint` interface (F6 in doc 24 §5) already exists in
tree as the seam that any of the options below would replace, restrict,
or formalize.

---

## 3. Options matrix

Scoring is **L / M / H** within each column (lower = better for cost
columns; higher = better for capability columns). "OPA-style delegation"
asks: does the option preserve a clean seam to externalize the policy
decision later? "Multi-attach preserved" asks: does the option respect
the structural reason containers own perms today?

| Option | Operator ergonomics | Audit-trail clarity | Read-path perf | Migration cost | Plugin-friendly | ABAC-ready | OPA-delegation | Multi-attach preserved |
|---|---|---|---|---|---|---|---|---|
| **A. Status quo + cascade endpoint (PERM-INHERIT-03)** | M (one extra call hides the dual model) | L (no new audit shape) | H (no change) | **L** (one endpoint) | M (plugin containers benefit automatically) | L | y (via existing F6 seam) | y |
| **B. Pure inheritance** (strip `:Permissions` from containers) | H *if* the operator never multi-attaches | L | H | H (loses multi-attach; data-model migration; affects HDF plugin) | L (containers must always have exactly one parent) | L | n (structural — perms become positional) | **n — breaking** |
| **C. External policy engine** (Casbin / SpiceDB / OpenFGA) | H (declarative rules, ABAC out of the box) | H (engine emits decision logs) | M-L (one extra hop; mitigated by ZedToken-style caching) | **XL+** (new sidecar, dual-write consistency, zedtokens/snapshots) | H (capabilities-as-config) | H | y (this is the design) | y |
| **D. Plugin-first `AuthzProvider` SPI** (formalize the F6 `PolicyDecisionPoint`) | M (today's behaviour preserved by default impl; institutes pick their own) | M (default impl unchanged; plugin impls can add their own audit) | H (default impl unchanged) | **M** (formalize existing seam: API contract, tests, docs) | **H** (matches CLAUDE.md "plugin-first" doctrine) | M-H (depending on plugin impl) | y (an OPA adapter is just one plugin) | y |
| **E. Graph-native ReBAC** (Zanzibar-style, perms as named edges) | H (every grant is a typed edge; query plan = authz plan) | H (graph IS the audit substrate; aligns with f(ai)²r) | M (more edges per check; mitigated by Cypher EXPLAIN tuning) | **L-M** (rewire the walk; no external dep; reuses Neo4j we already run) | M (plugin entity kinds register their walk-edges) | M (ABAC via attribute-bearing edges is awkward) | n-ish (delegation becomes "this graph is the policy"; OPA over the same graph adds little) | y |

### 3.1 Option A — argued against itself

A is "ship PERM-INHERIT-03 and stop." Its honest weakness: the cascade
endpoint **hides the dual model rather than resolving it**. The next
container kind a plugin adds will reproduce the footgun until someone
remembers to wire it into the cascade walk; the endpoint becomes a
maintenance debt indexed by "every container subclass." It also
doesn't address F1 / F3 / F6 (declarative seam, audit log,
policy-decision-point) — those remain on the L8 epic and the technical
debt clock keeps ticking. The cascade endpoint is the right
*operator-side* fix today, but as a *terminal* answer it accepts that
authz drift is an acceptable cost of plugin growth.

### 3.2 Option D — argued against itself

D is "formalize the existing F6 `PolicyDecisionPoint` into a plugin
SPI." Its honest weakness: **SPI is not policy.** Promoting an
interface to a contract doesn't itself answer the cascade question, the
ABAC question, or the audit-log question. It is a structural enabler
that lets *future* options (C, E, custom institute policies) ship as
plugins — but on day one it ships zero new operator capability.
Reviewers who weight "does this make tomorrow's grants easier?" higher
than "does this keep doors open?" will read D as architecture astronomy.
The honest answer is that D pairs cleanly with A (ship cascade today;
formalize the SPI in the same PR so the next plugin author can swap
their own policy), but D *alone* is not an answer.

---

## 4. Recommendation

**Ship A (PERM-INHERIT-03 cascade endpoint) AND D (formalize the
`AuthzProvider` SPI by promoting the existing F6 seam). Defer C and E
until ABAC requirements are concrete.**

Reasoning, with explicit weighting:

1. **Cascade-footgun frequency is high; ABAC-rule frequency is
   speculative.** Every showcase setup today hits the footgun
   ([`PERM-INHERIT-MATRIX §3`](../data/PERM-INHERIT-MATRIX.md#3-the-operator-footgun-containers-escape-the-cascade)).
   Concrete ABAC requirements ("only readable by same-institute
   users", "blocked while NCR is open") are designed but not requested
   by an operator. Weight: **cascade × 1.0, ABAC × 0.3**.
2. **Plugin-kind churn argues for SPI formalization now, not later.**
   The plugin catalogue is growing (HDF, Git, Video, Spatial, Image,
   Table, AAS in flight). Every new container kind adds a footgun
   surface; every new institute-deployment adds a policy-tail
   requirement. The CLAUDE.md "plugin-first" doctrine puts D ahead of
   in-tree alternatives on principle. Weight: **plugin-friendly × 1.0**.
3. **Read-path performance and graph-substrate alignment argue
   *against* C now.** SpiceDB/OpenFGA each maintain their own
   consistency model (zedtokens, snapshots, dual-write windows);
   Shepard's perm walks are read-mostly Neo4j queries already adjacent
   to the data graph. Migrating to an external engine optimises a
   problem (cross-service authz, large org structures) Shepard does
   not have. Weight: **perf × 0.7, migration-cost × 1.0**.
4. **E is the most architecturally seductive option and the wrong one
   to lead with.** ReBAC fits the substrate (Neo4j is Zanzibar without
   the paper). But: (a) the BUG-148 anti-regression locks in a
   constraint E would have to respect anyway (perms-as-edges still
   shouldn't attach to `:DataObject`); (b) the audit-trail-graph and
   f(ai)²r alignment is real but premature; (c) attribute-bearing edges
   for ABAC are awkward. E becomes the right answer **if** D's plugin
   surface attracts a Zanzibar-style implementation from a third party.
   Weight: **graph-alignment × 0.6 (real but defer)**.
5. **Operator ergonomics target: 1-call grant.** "Grant flo READ on
   this Collection and all its referenced containers" must be one
   API call. A solves that. Anything beyond A must preserve that
   ergonomic. D does. C and E both do if implemented carefully.

### 4.1 The recommendation, argued against itself

A+D is "ship today's operator fix and formalize the seam so future
options are cheaper." The opposing camp's strongest critique:
**this is incrementalism wearing the costume of strategy.** Shipping
the cascade endpoint *normalises* the dual ownership model — once
operators have a working one-call grant, the structural critique
loses its forcing function and the model petrifies. If the long-term
right answer is E (graph-native ReBAC), every month spent stabilising
A+D is a month spent making E's migration harder, because client
expectations crystallize around the cascade endpoint's behaviour.
Counter-argument: A and E are not incompatible — A's wire-shape can
be re-implemented on top of E without breaking clients, and D's SPI
is the seam through which E ships when ABAC requirements materialise.
But the critique is honest: A+D defers the structural question, and
the operator should know that.

### 4.2 Persona consult — opposing camps

Per [`feedback_agents_argue_and_consult.md`](../../MEMORY.md), two
roles from CLAUDE.md sit at opposite poles on this question:

**Role 4 — Industrial Manufacturing & Quality Engineer** pushes
hardest **toward C / D / E**. From the lens of EN 9100 audit
readiness and NCR routing, the future requirements are concrete:
"only readable by users from the same institute", "blocked for read
until concession approval is signed", "calibration certificate must
be readable by the inspector but not by the operator." These are
ABAC predicates; A solves none of them. The Role 4 vote is *don't
ship A as terminal — at minimum bundle D, and start the C/E
evaluation now so an audit-readiness story exists.*

**Role 9 — Reluctant Senior Researcher** pushes hardest **toward A
alone**. From the lens of a 28-year researcher whose Excel sheet
and NFS folders already solve access control by social convention
("I tell my collaborators where things are"), every layer added to
the authz model is a click between her and her data. Plugin SPIs
are abstractions for someone else's institute; ReBAC is a buzzword;
declarative annotations are a way for the system to deny her
something she could previously get. The Role 9 vote is *ship the
cascade endpoint, stop, and don't touch the rest.*

These camps don't reconcile cleanly. The A+D recommendation reads as
"give Role 9 the one-call grant and give Role 4 the SPI seam so
their institute can layer the ABAC policy on top without changing
core." It is a compromise, and a reviewer who weights either camp
disproportionately will reject it.

---

## 5. Migration sketch (A+D)

Three bullets, per the template:

- **Cutover shape.** Ship PERM-INHERIT-03's
  `POST /v2/collections/{appId}/permissions/cascade` endpoint (M-size,
  already designed) **in the same PR** as a new `AuthzProvider`
  interface in `de.dlr.shepard.auth.permission.spi` whose default
  implementation delegates to today's `PermissionsService` /
  `GraphPolicyDecisionPoint`. Cascade endpoint calls
  `authzProvider.cascadeGrant(...)`; default impl walks the matrix
  and applies PUTs. Plugins that ship their own container kinds get
  one method to override (`containerKindsToCascade()`); the cascade
  endpoint discovers them via CDI without core code changes.

- **Rollback hatch.** The SPI is purely additive — no existing API
  changes. If the SPI design is later judged wrong, the cascade
  endpoint and the default `AuthzProvider` impl stay shipped, and
  the SPI is deprecated as a contract without removing the underlying
  `PermissionsService` calls. Plugin authors who consumed the SPI
  get a one-cycle deprecation window; the wire-level cascade
  endpoint is unaffected.

- **Deprecation timeline.** None for v1. The `AuthzProvider` SPI
  is `experimental` (stage `idea` → `feature-defined` on first
  external plugin consumer; `audited-by-personas` after the next
  full perm-system review at L8 close). C and E evaluations are
  formally re-opened at L8 close, or when an operator files an ABAC
  requirement — whichever first.

---

## 6. Open questions (for operator + persona consult)

1. **Cascade endpoint scope: deep or shallow?** Does the cascade
   walk only the containers directly referenced by the Collection,
   or does it follow `DataObjectReference` / `CollectionReference`
   edges into other Collections? (Recommended: shallow with an
   opt-in `?deep=true`; matches principle of least surprise.)
2. **ABAC trigger threshold.** How many concrete ABAC requirements
   ("same-institute only", "NCR-gated", "license-restricted") need
   to materialise before C or E is re-opened as the right answer?
   One? Three? An EU-mandated compliance feature?
3. **`AuthzProvider` granularity.** Should the SPI surface be
   per-decision (`isAllowed(...)`), per-walk (`walkParents(...)`),
   or per-operation (`cascadeGrant`, `evaluateRequirement`, …)?
   The narrowest SPI that lets a future ABAC plugin ship without
   core changes is the target; the exact contract is the design
   work that would happen in D's implementing PR.
4. **Multi-Collection containers: keep or surface in UI?** The
   structural reason containers own perms is multi-attachability.
   Is that capability actually used today? If a container is only
   ever attached to one Collection in practice, the entire dual
   model is solving a non-problem and Option B becomes viable.
   (Empirical question; answerable by a one-shot Cypher count.)
5. **Audit-log shape for cascade.** F3 (audit log) is already on
   the L8 epic. Should a cascade-grant write one audit row
   (atomic intent) or N rows (one per cascaded PUT)? Affects the
   compliance question Role 4 raises.
6. **Where does the cascade endpoint live: `/v2/collections/`
   or `/v2/admin/permissions/cascade`?** The current PERM-INHERIT-03
   row says the former. Pre-instance-admin role gating, the
   admin path may be cleaner — but it duplicates the resource
   model.
7. **What is the right answer if the operator says "redesign
   anyway"?** The A+D recommendation assumes evolutionary
   pressure. If the operator has a strong prior toward graph-native
   authz (E) or external-engine ABAC (C), the cascade endpoint
   still ships (it's compatible with all three end-states), but the
   SPI design effort goes into the chosen target instead of D's
   default implementation.

---

## 7. Bibliography

Externally cited (entries added to
[`docs/_data/references.bib`](../../docs/_data/references.bib) in this PR):

- **Zanzibar paper** — Pang, R. et al. (2019). "Zanzibar: Google's
  Consistent, Global Authorization System." USENIX ATC 2019. Bib key:
  `pang2019zanzibar`. Cited for E's structural shape; the zedtoken
  consistency model is the reason C/E aren't free.
- **NIST SP 800-162** — Hu, V. et al. (2014). "Guide to Attribute
  Based Access Control (ABAC) Definition and Considerations." NIST.
  Bib key: `nist800162Abac2014`. Cited for the ABAC framing in §3.
- **OWASP Authorization Cheat Sheet** — OWASP Foundation (2024).
  Bib key: `owaspAuthzCheatsheet2024`. Cited for the "deny by default"
  and "centralize authorization logic" principles already satisfied
  by Shepard's C3 fail-closed + `PermissionsService` seam.
- **SpiceDB / authzed** — Authzed, Inc. (2024). "SpiceDB: Open Source,
  Google Zanzibar-inspired permissions database." Bib key:
  `authzedSpicedb2024`. Cited as the canonical OSS Zanzibar
  implementation for Option C's migration-cost estimate.
- **OpenFGA** — Cloud Native Computing Foundation / Okta (2024).
  "OpenFGA: Fine-grained authorization at scale, inspired by Google
  Zanzibar." Bib key: `openfgaZanzibar2024`. Cited as the alternative
  OSS Zanzibar implementation; useful for the SPI's plugin-impl
  surface if D evolves toward C.
