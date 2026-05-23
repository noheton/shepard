---
title: Persona audit — ADMIN-STALE-CH design (`aidocs/data/89`)
stage: deployed
last-stage-change: 2026-05-23
audience: contributors, reviewers, design-doc author
companion: aidocs/data/89-stale-channel-admin-design.md, aidocs/16-dispatcher-backlog.md (ADMIN-STALE-CH)
audited_doc_commit: f5d58ef9
audited_doc_stage_before: feature-defined
audited_doc_stage_after: audited-by-personas (proposed — IF all four ACCEPT-WITH-CHANGES)
personas:
  - Industrial Manufacturing & Quality Engineer (Role 4, primary)
  - Research Data Manager / FAIR steward (Role 5, secondary)
  - API Scrutinizer / Minimalist (Role 3, secondary)
  - Reluctant Senior Researcher (Role 9, secondary)
audit_round: 1
---

# Persona audit — ADMIN-STALE-CH (Stale timeseries channel admin tool)

Round 1 audit of `aidocs/data/89-stale-channel-admin-design.md` (committed
`f5d58ef9`, stage `feature-defined`). Per `feedback_persona_audit_triggers.md`
the trigger is the stage-flip request to `audited-by-personas`. Four personas
fire because the feature is a **destructive admin tool** — the default trio
(Strategy / API Scrutinizer / RDM) is augmented with Manufacturing-Quality
(EN 9100 lens) and Reluctant Senior (operator-side safety lens); Strategy
Aligner consultation is folded into the cross-persona reconciliation rather
than a separate section because the doc's §1, §8, §11 already lean
Strategy-heavy.

**Verification anchor (matters for every persona).** Before the audit, the
following greps were run against the codebase to discriminate hand-waved vs.
implemented claims:

| Claim in §89 | Verification result |
|---|---|
| `PublicationStateService` exists | **ABSENT** (zero hits in `backend/src/main/java/`). |
| `publicationState` field on `:DataObject` exists | **ABSENT** — the entity carries a `status` enum (`DRAFT|IN_REVIEW|READY|PUBLISHED|ARCHIVED`); there is no separate publication-state machine. Confirmed by `grep -rn "publicationState\|PublicationState" backend/src/main/java/`. |
| `audit_classified` attribute exists today | **ABSENT** (zero hits in `backend/src/`). The doc invents the gate without specifying its definition / migration / write-path. |
| `chameo:hasCalibration` calibration-cert linkage | **ABSENT** (zero hits). Doc acknowledges "when ONT1d ships" but uses it as a current immunity check. |
| `ProvenanceCaptureFilter` exists | **PRESENT** (`backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java`). PROV1a wiring claim is accurate. |
| `:Timeseries` label + `HAS_PAYLOAD` relationship | **PRESENT** (`context/references/timeseriesreference/model/ReferencedTimeseriesNodeEntity.java:16`; `TimeseriesReference.java:74` via `Constants.HAS_PAYLOAD`). §3 Cypher path is wire-correct. |
| `shepard_id` column on `timeseries` | **PRESENT** (`V1.11.0__add_shepard_id_to_timeseries.sql`). TS-IDa shipped claim verified. |
| `V1.12.0__add_deleted_at_to_timeseries.sql` migration | **ABSENT** (planned in §12 ADMIN-STALE-CH-c). |
| `:StaleChannelConfig` singleton | **ABSENT** (planned). |
| `/admin/storage/stale-channels` Vue route | **ABSENT** (planned). |
| `AdminStorageOverviewRest` (cited as pattern to extend) | **PRESENT** (`v2/admin/resources/AdminStorageOverviewRest.java`). |

The audit anchors specific findings to these verification rows. Hand-waved
claims do not get accepted as constraints — they get raised as CHANGE-REQUESTS.

---

## §1 Industrial Manufacturing & Quality Engineer (Role 4 — PRIMARY)

**Lens:** EN 9100 immune-list, calibration-traceability preservation,
audit-classified channel immunity. Applied to §7 safety + §13 ship criteria.

### Lens citations + research

- AS9100D / EN 9100 records retention is **customer-driven**, not standard-mandated.
  Typical aerospace retention windows are 7–40 years; major customers commonly
  require 10–15 years; safety-critical 30–40 years
  ([BPRHub on AS9100D records retention](https://www.bprhub.com/blogs/as9100d-record-retention);
  [Elsmar discussion on AS9100D retention](https://elsmar.com/elsmarqualityforum/threads/flowdown-of-aerospace-record-retention-requirements-as9100.68185/);
  [EN 9130:2020 industry guidance summary](https://www.bprhub.com/blogs/as9100d-record-retention)).
- The flow-down model means a Shepard instance serving a Tier-1 aerospace
  programme **inherits whatever retention window is in the contract** — it
  cannot offer a 30-day soft-delete-then-purge as the only policy and meet a
  10-year contract. Any deletion path must be either disabled per-container
  for those workloads, or gated by the retention window the contract requires.

### Findings (Role 4)

**F4-1 [CRITICAL]. `audit_classified` is introduced as a hard immunity gate
in §7 but does not exist in the codebase, has no migration plan, no defined
values, and no write-path.** Anchor: `aidocs/data/89 §7 "Lens-by-lens
argument → Manufacturing-Quality (1)"`; verification grep `grep -r
"audit_classified" backend/src/` returns zero hits. Either the gate is
operational on day 1 (add a §"introducing `audit_classified`" sub-section
specifying the attribute key, its values, where it lives — node-level or
edge-level or annotation —, the migration that adds the index supporting
the lookup, and the admin surface for setting it) OR it is removed as an
immunity claim until ONT1d ships and replaced with the bare statement "no
in-tree quality immunity in v1." Lens: Manufacturing-Quality —
without the attribute defined, the EN 9100 promise in §1 is a claim with no
implementation behind it. **What would change my mind:** a paragraph that
says "audit-classified is the existing `:DataObject.status = 'PUBLISHED'`
combined with `:Annotation{key='audit_classified', value='true'}` — the
admin sets it via the existing annotations UI" — but the doc must say so
and the lookup path must be validated.

**F4-2 [CRITICAL]. Calibration linkage immunity (§7 (2)) is conditional on
`chameo:hasCalibration` which doesn't exist today.** Anchor: `aidocs/data/89
§7 "Lens-by-lens argument → Manufacturing-Quality (2)"`. The fallback is
"the temporary `calibration_cert_id` annotation key today" — but this key is
neither documented in the seed scripts (`examples/lumen-showcase/seed.py`,
`examples/mffd-showcase/seed.py`) nor defined as a controlled vocabulary
term. Today, **no channel anywhere in a live Shepard instance carries
calibration linkage**, which means the gate is effectively a no-op. An
aerospace auditor reading "calibration-linked channels are immune" would
expect to see (a) the controlled-vocabulary term defined, (b) at least one
instrument's calibration cert linked in the demo dataset, (c) a UI surface
for an operator to attach a cert. None exist. Lens:
Manufacturing-Quality — this is the difference between a real immune-list
and a stub that catches nothing. **What would change my mind:** a §"Calibration
linkage today" sub-section that documents the in-the-meantime key
(`calibration_cert_id`), seeds at least one MFFD channel with it, and adds
the lookup query to the implementation roadmap as ADMIN-STALE-CH-c1.

**F4-3 [MAJOR]. The 30-day soft-delete + hard-delete-after-30 model is
incompatible with EN 9100 / AS9100 contract retention.** Anchor: `aidocs/data/89
§7 "Soft-delete: argued → Decision Option (1)"`; external evidence —
typical aerospace customer retention 10–15 years
([BPRHub](https://www.bprhub.com/blogs/as9100d-record-retention)). An MFFD
container under a 10-year retention contract cannot have channels
hard-deleted after 30 days, even with admin approval. The §1 mention of an
EN 9100 lens makes the omission worse — the doc claims the tool serves
that audience while shipping a policy that violates the audience's
constraints. Lens: Manufacturing-Quality. **What would change my mind:**
the §9 SM1-relationship table grows a row "per-container retention policy
override — channels in a container with `retentionDays > 30` are NEVER
hard-deleted by this tool's background job" + an `:AuditClassifiedContainer`
or `retention_days` attribute on `:TimeseriesContainer` that the background
job consults. The 30-day window remains the default for non-aerospace
workloads; aerospace workloads opt in to longer retention.

**F4-4 [MAJOR]. The "rework loop / NCR" trace pattern from
`aidocs/agent-findings/manufacturing-quality.md` is not consulted.**
Anchor: cross-reference — earlier persona findings (RDM + MQ) flagged that
the predecessor chain plus NCR routing IS the audit trail. A channel
attached to a NCR or rework DataObject (e.g. TR-004's investigation
sub-tree, or the MFFD Q1 ply-5 NDT-FAIL → Rework chain) carries quality
significance beyond "the latest revision references it." The orphan
detection in §3 step 4 uses `do.deleted IS NULL OR do.deleted = false`
but does not check whether the DataObject is on an NCR-resolution path
where past-revision channels are themselves the audit evidence. Lens:
Manufacturing-Quality. **What would change my mind:** the §7 immune-list
adds a row "channels reachable from any `:DataObject` whose
`:Annotation{key='quality_state', value IN ['NCR_OPEN', 'REWORK',
'CONCESSION']}` chain is immune" — or the doc explicitly states "v1 does
not detect NCR linkage; reconcile when QM1 (`aidocs/16` quality-state
work) ships."

**F4-5 [MINOR]. The `reason` field is mandatory but free-text only — an
auditor cannot filter on "show me every delete made under change-order
CO-2026-447".** Anchor: `aidocs/data/89 §4 Wire-shape principles (2)`.
For an aerospace audit pass, structured reason metadata (a change-order
ID, an NCR ID, a justification taxonomy) carries more weight than free-text.
Lens: Manufacturing-Quality. **What would change my mind:** the `reason`
shape evolves to `{ text: "...", changeOrderId: null, ncrId: null,
justificationCode: null }` with PROV1a indexing on the structured fields;
v1 can keep the free-text as required + the structured fields as optional
to avoid blocking initial ship.

### Verdict (Role 4): **ACCEPT-WITH-CHANGES**

The design is structurally sound — the in-transaction re-verify (HARD-REQ-1),
the dry-run-as-default, the explicit `refused[]` array are correct for
destructive admin work. But the immunity claims that protect aerospace data
(§7 Manufacturing-Quality lens) are written as constraints when in fact they
are TODOs — F4-1 and F4-2 are CRITICAL because the doc's own §13 ship
criteria do not block on them, which means v1 could ship with the immunity
claims unfulfilled. F4-3 (30-day window vs. 10-year contracts) is the second
edge that needs a per-container override before this ships near an
aerospace container.

**Change requests:** F4-1 (close audit_classified definition gap), F4-2
(close calibration-linkage definition gap or document the stub), F4-3
(per-container retention override).

---

## §2 Research Data Manager / FAIR steward (Role 5 — SECONDARY)

**Lens:** DataCite citation immortality, publication-state immunity, ORCID /
DOI / external-reference detection, Unhide harvest impact. Applied to §7
immunity list and §13 acceptance criteria.

### Lens citations + research

- **DataCite policy: DOIs cannot be deleted.** A registered DOI persists
  even when the underlying object is withdrawn; the canonical pattern is a
  "tombstone page" with the DataCite repository keeping the DOI metadata in
  `Registered` state pointing to the tombstone URL
  ([DataCite — Best Practices for Tombstone Pages](https://support.datacite.org/docs/tombstone-pages);
  [DataCite — DOI Persistence](https://support.datacite.org/docs/doi-persistence)).
- This means: any channel whose backing DataObject has ever been issued an
  external persistent identifier is **archival** — the bytes the DOI points
  to (or a tombstone-and-metadata pair) must survive. Even with admin
  approval, hard-deleting such a channel breaks the DataCite contract.
- The Welzmüller et al. PLUTO RDM paper ([DLR eLib 215120](https://elib.dlr.de/215120/))
  motivates the citation-immortality requirement that the §89 RDM
  paragraph cites — but the doc does not operationalise it.

### Findings (Role 5)

**F5-1 [CRITICAL]. Publication-state immunity is delegated to a service
that doesn't exist.** Anchor: `aidocs/data/89 §7 "Lens-by-lens argument →
RDM"` — quote: *"Implementation hooks the existing `PublicationStateService`
(or, if absent, deferred to RDM1 with this tool's release-gate)."*
Verification grep: `grep -r "PublicationStateService" backend/src/main/java/`
returns zero hits. There is no service. There is no field. There is a
`status` enum on DataObject (`DRAFT|IN_REVIEW|READY|PUBLISHED|ARCHIVED`) but
the doc does NOT specify that. So today, the RDM immunity gate is a TODO
labelled as a constraint, and the §13 ship criteria do not list "publication
immunity gate verified" as a blocker. Lens: RDM. **What would change my
mind:** §13 grows a ship criterion #7 "v1 refuses delete on any channel
whose backing `:DataObject.status = 'PUBLISHED'` OR `'ARCHIVED'`,
implemented in ADMIN-STALE-CH-c and verified by a test fixture." Either
the constraint is operational on day 1 or the §7 paragraph is reworded to
"v1 ships with NO publication-state immunity — RDM1 will add the gate; until
then, operators must not run this tool on instances with published data."

**F5-2 [CRITICAL]. DOI / external-PID immunity is not mentioned at all.**
Anchor: gap — no §7 paragraph addresses external persistent identifiers.
Per DataCite policy ([Tombstone pages best practice](https://support.datacite.org/docs/tombstone-pages))
and the §1 RDM paragraph's own claim that "F(AI)²R and FAIR both require
that 'data reachable but not referenced' be either re-attached or
deleted; a permanent third category is not acceptable" — but FAIR ALSO
requires that data with an external persistent identifier remain
accessible. The two constraints intersect: an orphan channel may also
carry a DOI. The doc must clarify which wins. Lens: RDM. **What would
change my mind:** add §7 row 5 "**Externally cited immunity.** Any
channel whose backing DataObject carries a DOI or other external PID
(detected via a `:Annotation{key='doi'|'handle'|'epic_pid'}` or via a
future `:DataObject.persistentIdentifier` field) is immune. The delete
endpoint refuses with `reason: 'externally_cited'`. PROV1a records the
attempt." Even if v1 only checks the annotation key, the gate must exist;
otherwise the tool can silently violate the DOI persistence contract that
DataCite enforces.

**F5-3 [MAJOR]. Helmholtz Unhide harvest impact is not addressed.**
Anchor: cross-reference `aidocs/integrations/67-unhide-publish-plugin.md` —
when a Shepard collection is exposed to Unhide via the harvest API, the
harvest snapshot includes the channel-level metadata. Deleting a channel
after a harvest creates a stale entry in the downstream catalog
(re3data / Helmholtz Databus / OpenAIRE indirectly). The §1 RDM lens
mentions FinOps + provenance hygiene but skips the downstream-catalog
discoverability question. Lens: RDM + Ecosystem Advocate (secondary
consultation). **What would change my mind:** §7 grows a paragraph
"**Harvest-exposed immunity.** Channels reachable from a
`:DataObject` whose container is harvest-exposed (`UnhideConfig` includes
the container) are flagged in the response as `harvestExposed: true`
in the bucket detail. The delete endpoint warns (does NOT refuse) and the
UI surfaces a stronger confirmation. Hard-delete of harvest-exposed
channels generates a DataCite-style tombstone record in `:Activity` for
the downstream catalog reconciliation flow."

**F5-4 [MAJOR]. The 30-day soft-delete window is shorter than typical
DMP retention horizons.** Anchor: `aidocs/data/89 §7 "Decision Option (1)"`.
DFG / Horizon Europe / Clean Aviation JU funded data has DMP-mandated
retention horizons typically 10 years (DFG: 10 yrs minimum; Horizon Europe:
"as long as needed"; Clean Aviation JU: programme lifecycle + 5 yrs after).
A 30-day recovery window is fine for genuinely scratch data but invisible
relative to the DMP horizon — once hard-deleted, the data is gone from
Shepard but the DMP says "retained for 10 years." Lens: RDM. **What would
change my mind:** the soft-delete-to-hard-delete cadence is admin-configurable
via `:StaleChannelConfig.recoveryWindowDays` (default 30, max 3650), with
the §7 paragraph explicitly tying long-window operators to DMP compliance.

**F5-5 [MINOR]. The `reason` field is required (good) but the audit trail
does not surface the **funding source / project ID** the deletion was made
under.** Anchor: `aidocs/data/89 §4 Wire-shape principles (2)`. For DMP
compliance reporting, the reason chain "X channels deleted under project
Y on date Z by admin A" is the auditable shape. Free-text reason is the
minimum; the funding-project tag is the upgrade. Lens: RDM. **What would
change my mind:** the `reason` shape evolves (per F4-5) to include
`fundingProjectId`. v1 can keep this optional.

### Verdict (Role 5): **ACCEPT-WITH-CHANGES (conditional on F5-1 + F5-2 in v1)**

The RDM concerns map directly to F5-1 (publication-state gate is a TODO not
a constraint) and F5-2 (DOI / external-PID immunity is missing entirely).
Both are CRITICAL because the doc claims FAIR compliance in §1 while
shipping no enforcement mechanism. F5-3 (Unhide harvest) and F5-4 (30-day
vs. DMP horizons) are MAJOR — they can be filed as follow-on rows under
SM1, but the design doc should at least acknowledge them as known gaps.

**Change requests:** F5-1 (operational publication-state gate or explicit
"not yet" with operator warning), F5-2 (external-PID immunity in v1 or
explicit "not yet"), F5-3 (Unhide-harvest-aware UI flag), F5-4
(admin-configurable recovery window).

---

## §3 API Scrutinizer / Minimalist (Role 3 — SECONDARY)

**Lens:** dry-run-as-default, RFC 9457 (RFC 7807 obsoleted Jul 2023) typed
error responses, the `refused[]` array shape, idempotency on §4 DELETE,
endpoint surface minimalism.

### Lens citations + research

- **RFC 7807 was obsoleted by RFC 9457 in July 2023**
  ([RFC 9457 — Problem Details for HTTP APIs (IETF datatracker)](https://datatracker.ietf.org/doc/rfc9457/);
  [Redocly — RFC 9457: Better information for bad situations](https://redocly.com/blog/problem-details-9457);
  [Swagger — Problem Details (RFC 9457): Doing API Errors Well](https://swagger.io/blog/problem-details-rfc9457-doing-api-errors-well/)).
  The doc cites RFC 7807 in §4 + §7. The wire shape is largely compatible but
  the **canonical citation should be RFC 9457** — explicitly the spec for
  "representing multiple problems" (relevant to the `refused[]` array).
- The 207 Multi-Status vs. 200-with-receipt question for bulk partial-success
  has two camps: 207 is the formal-correctness choice (one HTTP status per
  item); 200-with-receipt is the AWS / Stripe practice (DynamoDB
  BatchWriteItem, SQS DeleteMessageBatch)
  ([Zalando — guidelines on batch/bulk requests and 207](https://github.com/zalando/restful-api-guidelines/issues/127);
  [SPS Commerce — Bulk Operations standard explicitly advises against 207](https://spscommerce.github.io/sps-api-standards/standards/bulk.html);
  [OneUptime — How to Handle Partial Success in Bulk API Operations](https://oneuptime.com/blog/post/2026-02-02-rest-bulk-api-partial-success/view)).

### Findings (Role 3)

**F3-1 [MAJOR]. Doc cites RFC 7807, which was obsoleted by RFC 9457 in
July 2023.** Anchor: `aidocs/data/89 §4 Wire-shape principles (5)`, `§7
Lens-by-lens API Scrutinizer paragraph`. RFC 9457 introduces the "multiple
problems" pattern via the `errors[]` member — directly applicable to the
§4 `refused[]` array shape. Lens: API Scrutinizer. **What would change my
mind:** doc is updated to cite RFC 9457 throughout, with a parenthetical
"(obsoletes RFC 7807; structure remains compatible)" and the `refused[]`
shape is documented as conformant to the 9457 multi-problem pattern.

**F3-2 [MAJOR]. `POST /delete` returning 200 + `refused[]` rather than 207
Multi-Status is the right Stripe/AWS-style choice for this domain, but the
doc does not argue the trade-off explicitly.** Anchor: `aidocs/data/89 §4
Wire-shape principles (4)`. Per the API Scrutinizer rule (`feedback_agents_argue_and_consult.md`),
"recommendations without falsifiability — 'X is good' without 'X would be
bad if Y' — are rejected." The doc says "Soft refusal on race ... does NOT
500 — it returns 200 with `refused: [{shepardId, reason}]`" without
arguing why this is better than 207 Multi-Status. Lens: API Scrutinizer.
**What would change my mind:** §4 grows a "Status-code rationale" paragraph
arguing the Stripe/AWS pattern (caller treats the response body as the
source of truth; 207 is heavier weight for clients that have to parse
per-item codes; 200-with-receipt is the standard for batch idempotent
operations [Zalando recommendation cited above]), and explicitly states
"we would switch to 207 if (a) the response gained items that aren't
delete operations or (b) per-item HTTP semantics like cache headers became
meaningful." This is the falsifiability bar.

**F3-3 [MAJOR]. `POST /delete` is not idempotent and the doc does not say
how to make it so.** Anchor: `aidocs/data/89 §4 Endpoint inventory row 5`.
A retry after a network blip (the caller doesn't know if the first call
succeeded) re-runs the delete, which is fine if the shepardIds were
already soft-deleted (they're now no-ops) but BAD if a later admin
recovered some of them in the meantime — the retry deletes them again
without re-verification. Lens: API Scrutinizer. **What would change my
mind:** the endpoint accepts an `Idempotency-Key` header (per Stripe's
[Idempotent Requests pattern](https://stripe.com/docs/api/idempotent_requests)),
caches the receipt for 24 hours, and replays it on duplicate-key collision.
The §13 ship criteria add "idempotency-key replay test." This is a small
addition but it's the difference between "destructive admin tool a CLI
script can safely retry" and "destructive admin tool that requires
single-shot delivery."

**F3-4 [MAJOR]. Bulk-delete request bodies have no documented size cap;
the response has no truncation policy.** Anchor: `aidocs/data/89 §4 Body
shape for /delete`. A caller submitting `shepardIds: [...]` with 100k
entries blows the request body size, the in-tx delete time, and the
response cap. Lens: API Scrutinizer. **What would change my mind:** §4
adds a "Limits" paragraph: max `shepardIds.length = 1000` per request
(returns 422 with `problem.type = "shepard-ids-too-many"` + a hint to
batch), default 50; the response envelope adds `truncated: false` and
`continuationToken: null` for forward compatibility with streaming.

**F3-5 [MAJOR]. No `/recover` endpoint is specced.** Anchor: gap — §5
UI references "Undo within 30 days" toast and §13 ship criterion 3 says
"admin can recover a deleted channel within 30 days; after 30 days, the
background job hard-deletes and recovery returns 410 Gone" — but the §4
endpoint inventory has no `POST /recover` entry. The CLI surface has no
`recover` command. The Vue route has no `/admin/storage/stale-channels/recovered`
view. This is an asymmetry: easy to delete, no documented path back.
Lens: API Scrutinizer (+ Reluctant Senior secondary). **What would change
my mind:** §4 grows endpoint row 8 + 9: `POST /v2/admin/timeseries/stale-channels/recover`
(body: `{ shepardIds: [...], reason: "..." }`, returns the same receipt
shape) and `GET /v2/admin/timeseries/stale-channels/recoverable` (paginated
list of `deleted_at IS NOT NULL` rows still inside the recovery window).
The CLI grows `shepard-admin stale-channels recover --shepard-id <id>` and
`list-recoverable`. §5 grows a `/recovered` tab.

**F3-6 [MINOR]. `containerAppId` query param uses appId (good — single-field
identity), but the doc never explicitly states the relationship to the
legacy `containerId` (long Neo4j ID).** Anchor: `aidocs/data/89 §4 Endpoint
inventory GET` query params. Per `aidocs/platform/87` the canonical
identity is `appId`; the doc should explicitly say "container is addressed
by `containerAppId` only; legacy `containerId` long-IDs are NOT accepted by
this surface — it's a fork-only `/v2/` endpoint, not /shepard/api/." Lens:
API Scrutinizer (5-tuple-smell relative). **What would change my mind:**
§4 query-param table explicitly types `containerAppId: UUID v7`.

**F3-7 [MINOR]. The §4 GET endpoint has no `Cache-Control` or `ETag`
guidance.** Anchor: `aidocs/data/89 §4 GET`. An admin polling the list every
30 seconds while planning a cleanup would benefit from conditional GETs.
v1 can defer; the doc should at least say so. Lens: API Scrutinizer.

### Verdict (Role 3): **ACCEPT-WITH-CHANGES**

The shape is right — single-field `shepardId` identity, `dryRun=true`
default, mandatory `reason`, PROV1a capture, `refused[]` array surfacing
race-skips. The issues are about completeness (F3-5 the missing recover
endpoint is the biggest), specification freshness (F3-1 RFC 9457), and
argued trade-offs (F3-2 the 200-vs-207 choice). F3-3 idempotency is the
hidden production bug that a script author would hit on first network
flake.

**Change requests:** F3-1 (cite RFC 9457), F3-2 (argue the 200-vs-207
choice), F3-3 (Idempotency-Key support + ship criterion), F3-4 (request
size limits), F3-5 (specify the recover endpoint).

---

## §4 Reluctant Senior Researcher (Role 9 — SECONDARY)

**Lens:** "the destructive button should always have an 'are you sure'
modal" + the 28-year-veteran's "I will not adopt a system that quietly
garbage-collects." Applied to §5 UI flow and §7 soft-delete window adequacy.

### Lens citations + research

- Confirmation-dialog UX research: text-entry confirmation ("type DELETE to
  confirm") is the gold standard for destructive irreversible operations
  ([UX Movement — How to Make Sure Users Don't Accidentally Delete](https://uxmovement.com/buttons/how-to-make-sure-users-dont-accidentally-delete/);
  [LogRocket — Double-check user actions: All about warning message UI](https://blog.logrocket.com/ux-design/double-check-user-actions-confirmation-dialog/);
  [UX Psychology — How to design better destructive action modals](https://uxpsychology.substack.com/p/how-to-design-better-destructive)).
  Double-confirmation is justified when (a) the action is irreversible and
  (b) the consequences are non-trivial — both apply here.
- Soft-delete-as-anti-pattern critique: the pattern looks helpful upfront
  but creates cascading problems at scale — query complexity, foreign-key
  invariants, unique-constraint violations
  ([Cultured Systems — Avoiding the soft delete anti-pattern](https://www.cultured.systems/2024/04/24/Soft-delete/);
  [Hacker News discussion](https://news.ycombinator.com/item?id=40326815);
  [Tree Web Solutions — Soft-deletion is actually pretty hard](https://treewebsolutions.com/articles/soft-deletion-is-actually-pretty-hard-14)).
  The Shawn-Mendes-double-booking case (a soft-delete filter removed →
  hundreds of duplicate seat sales) is the cautionary tale; for Shepard the
  parallel risk is "an admin recovers a soft-deleted channel after a new
  channel reused the 5-tuple."

### Findings (Role 9)

**F9-1 [MAJOR]. The `v-stepper` confirm flow does not include a text-entry
confirmation for the CONFIRMED-STALE bucket — the destructive button is one
click away.** Anchor: `aidocs/data/89 §5 Bulk-delete flow row`. Per
[UX Movement](https://uxmovement.com/buttons/how-to-make-sure-users-dont-accidentally-delete/),
for irreversible operations on >1 row the typed-confirmation ("type
DELETE-9 to confirm 9 channels") halves the accidental-click rate. The doc's
"two-step destructive-action gate" via `v-stepper` is acceptable for the
PROBABLY-STALE bucket (where the admin is already cautious) but
under-protects against the keyboard-walking accidental click on the
CONFIRMED-STALE happy path. Lens: Reluctant Senior. **What would change my
mind:** §5 acceptance criteria adds "for deletes of ≥10 channels OR for
any selection that includes a PROBABLY-STALE row, the v-stepper step 3
requires the admin to type the channel count (e.g. '9') before
the delete button enables." For single-row deletes on CONFIRMED-STALE the
existing reason-field requirement is sufficient.

**F9-2 [MAJOR]. The 30-day soft-delete window is the chosen Option (1) but
the §7 rationale skips the soft-delete anti-pattern critique entirely.**
Anchor: `aidocs/data/89 §7 "Soft-delete: argued"`. The doc compares three
options (deleted_at / trash schema / no soft-delete) but does not surface
the well-documented soft-delete anti-pattern concerns
([Cultured Systems](https://www.cultured.systems/2024/04/24/Soft-delete/);
[Hacker News thread](https://news.ycombinator.com/item?id=40326815)) —
specifically, **what happens when an admin creates a new channel with the
same 5-tuple as a soft-deleted one, then recovers the soft-deleted one?**
The §3 detection query has no unique constraint on (5-tuple, deleted_at IS
NULL) and the §7 recovery path has no collision check. Lens: Reluctant
Senior (+ API Scrutinizer secondary). **What would change my mind:** §7
grows a "Soft-delete collision handling" sub-section: the recovery endpoint
checks for a live row with the same 5-tuple and either (a) refuses with
`reason: "5tuple_collision"` and points to the conflicting `shepardId`, or
(b) reattaches the live row's points to the recovered shepardId. The doc
must pick. Also: the V1.12 migration adds a partial unique index
`(container_id, measurement, field, device, location, symbolic_name) WHERE
deleted_at IS NULL` to prevent the collision at insert time.

**F9-3 [MAJOR]. The background hard-delete job is referenced (§13 ship
criterion 3, §7 chosen Option 1) but its specification is missing.**
Anchor: gap — no §7 sub-section, no §12 sub-row, no §13 acceptance
criterion specifies (a) when the job runs (cron expression), (b) where it
logs, (c) whether an admin can pause it during an investigation
("we just found a regression; pause hard-deletes for the next 14 days"),
(d) what happens if the job crashes mid-batch. Lens: Reluctant Senior — a
28-year-veteran sysadmin knows that "background job that quietly removes
data" needs an operator-visible kill switch. **What would change my mind:**
§7 grows a "Background hard-delete job" sub-section specifying (a) Quarkus
`@Scheduled` daily at 02:30 UTC, (b) operator config
`shepard.stale-channels.hard-delete-job.enabled=true` (default) +
`...cron="0 30 2 * * ?"`, (c) admin REST endpoint
`POST /v2/admin/timeseries/stale-channels/pause-hard-delete` + CLI parity,
(d) the job logs each delete to `:Activity` with a `system_actor` rather
than a human admin, captured by the same activity-type taxonomy as
manual deletes.

**F9-4 [MAJOR]. The TimescaleDB compressed-chunk delete blow-up risk is
mentioned (§11 R1) but the mitigation is incomplete relative to documented
cases.** Anchor: `aidocs/data/89 §11 R1`. A documented TimescaleDB case
showed a 25 GB compressed hypertable expanding to 330 GB after a delete
that touched many compressed chunks
([TimescaleDB GitHub #6196](https://github.com/timescale/timescaledb/issues/6196)).
The doc's mitigation is "order deletes by `last_write_ns ASC` so old
(likely-compressed) data goes first, chunk into batches of N=50." But the
real risk is the **disk-space inflation** during the
decompress-modify-recompress cycle — which the disk monitor sees as
storage GROWING during a cleanup operation. Lens: Reluctant Senior — "the
disk filled up while I was trying to free space" is the kind of moment
that ends adoption. **What would change my mind:** §11 R1 grows a
sub-paragraph "**Pre-flight free-space check.** Before any delete batch, the
service reads the current hypertable size + the largest compressed chunk
size and aborts the batch (with `problem.type = 'insufficient-headroom'`)
if `free_space < 2 × largest_chunk_size`. TimescaleDB 2.21+'s
batch-deletion logic [Tiger Data — 42× Faster DELETEs](https://www.tigerdata.com/blog/42x-faster-deletes-accelerating-analytics-and-high-volume-ingestion-with-timescaledb-2-21)
mitigates this on entire-chunk deletes but does not eliminate the
intermediate inflation; the headroom check is the operator-friendly
defence."

**F9-5 [MINOR]. The empty-state copy "No stale channels detected. Storage
is clean." is good (RDM positive signal) but the section header should
mirror it.** Anchor: `aidocs/data/89 §5 Acceptance criteria last bullet`.
A 28-year veteran wants to skim the page header to know whether anything
needs action. Lens: Reluctant Senior. **What would change my mind:** §5
grows a "Page header convention" sub-line "Page header reflects state:
'Storage hygiene — clean ✓' on empty, 'Storage hygiene — N channels need
review' otherwise."

### Verdict (Role 9): **ACCEPT-WITH-CHANGES**

The Reluctant Senior would not reject this tool outright — the dry-run
default, the `v-stepper` flow, the reason field, and the 30-day soft-delete
window are all gestures in the right direction. But the doc skips three
operationally-critical specifications: F9-2 (5-tuple collision on recovery)
is a silent data-loss vector parallel in spirit to HARD-REQ-1; F9-3 (the
pause-able background job) is the "I need to stop the bleeding"
operator switch every veteran asks for; F9-4 (free-space pre-flight) is
the difference between "storage cleanup" and "storage emergency". F9-5 is
polish.

**Change requests:** F9-1 (type-to-confirm for ≥10 rows or PROBABLY-STALE
selections), F9-2 (5-tuple collision policy on recovery + partial unique
index), F9-3 (background job spec + pause control), F9-4 (free-space
pre-flight check before each batch).

---

## §5 Cross-persona reconciliation

### Where personas reinforce each other (FIRE TOGETHER)

| Concern | Personas | Reconciliation |
|---|---|---|
| Publication-state immunity is a TODO labelled as a constraint | RDM (F5-1) + Manufacturing-Quality (F4-1 audit_classified parallel) | The two CRITICAL findings together say the same thing: §7 promises immune-list protections that don't exist in code. The doc cannot ship at `audited-by-personas` with the immune-list as a hand-waved future. **Synthesis:** either operationalise both gates in v1 (using existing `:DataObject.status` for RDM and a new `:Annotation{key='audit_classified'}` for MQ) OR rewrite §7 to be a "v1 has NO immunity gates — operators must not deploy on instances with audit-classified or published data" warning, with a `STALE_CHANNEL_ENABLED` feature toggle defaulted OFF until ONT1d + RDM1 land. |
| Missing /recover endpoint | API Scrutinizer (F3-5) + Reluctant Senior (F9-2/F9-3) | The endpoint gap is the proximal symptom of a broader "recovery story is half-spec'd" problem. The collision policy (F9-2), the recover endpoint shape (F3-5), and the pause-able hard-delete job (F9-3) together form one §7 sub-section "Recovery + reversal: full specification." **Synthesis:** §4 + §7 grow a coherent "recover" path; §13 ship criteria gain a "recover end-to-end test" line. |
| 30-day window is too short for serious workloads | RDM (F5-4) + Manufacturing-Quality (F4-3) | Both lenses arrive at "admin-configurable recovery window" from different angles (DMP horizon vs. EN 9100 contract retention). **Synthesis:** `:StaleChannelConfig.recoveryWindowDays` is admin-configurable (default 30, max 3650). The `:TimeseriesContainer` may carry a `retention_days` attribute that overrides the cluster-wide value. |
| Reason field is free-text only | Manufacturing-Quality (F4-5) + RDM (F5-5) | Both want structured reason fields. **Synthesis:** `reason: { text: required, changeOrderId: optional, ncrId: optional, fundingProjectId: optional, justificationCode: optional }` — v1 ships the structure with text-only required; later releases mandate specific fields per container. |

### Where personas trade off (DISAGREE — resolve explicitly)

| Concern | Persona A | Persona B | Resolution |
|---|---|---|---|
| Type-to-confirm friction vs. accidental-click cost | Reluctant Senior wants typed confirmation for ≥10 rows (F9-1) | API Scrutinizer wants minimal endpoint surface — the typed confirmation is client-side, has no API cost; no conflict actually. | No real conflict. Implement F9-1 client-side only. |
| 207 Multi-Status vs. 200 + receipt | (Latent) — API Scrutinizer's lens could argue 207 is formally correct | API Scrutinizer's actual lean: 200 + receipt (Stripe/AWS pattern) per F3-2 | Resolved in F3-2: ship 200 + receipt + argued rationale + the falsifiability bar. |

### What no persona flagged but the doc author should consider

- **Race window between bulk-delete and concurrent admin operations.** §7
  HARD-REQ-1 protects against new `:TimeseriesReference` creation between
  detection and delete, but does NOT protect against a concurrent admin
  running `POST /delete` with the same shepardIds (race between two
  admins). Idempotency-Key (F3-3) partially mitigates this if both admins
  use the same key (they wouldn't). The mitigation is `SELECT ... FOR
  UPDATE` on the rows being deleted inside the §7 transaction; if the
  lock cannot be acquired in 5s, the operation aborts. (Adviser's note,
  no persona explicitly raised it.)

---

## §6 ESCALATIONS

### E1 — SM1 integration question

**Status:** RESOLVED in the doc, but **needs strengthening**.

The doc's §8 + §9 are clear that this tool is an in-tree sub-feature of SM1
(#74), not a plugin, not a separate top-level feature. The plugin-first
exception list (security perimeter, identity primitives, PROV1a) applies
correctly. The cross-persona reconciliation does NOT change this — none of
the four personas argued for a plugin shape.

However: the §9 SM1-relationship table claims "Orphan default = infinite
grace + nag" is implemented "via PROV1a recurring activity" — that's the
nag mechanism, but the "infinite grace" claim is contradicted by the 30-day
hard-delete window in §7. **Recommendation to the doc author:** rewrite the
§9 row to say "this tool overrides SM1's default 'infinite grace' for
channels — operators who want infinite grace can set
`recoveryWindowDays = max` (or disable the background hard-delete job)."
This is a cosmetic but important alignment with the SM1 design intent.

### E2 — Audit-classified-channel immunity edge cases

**Status:** ESCALATED. Two persona findings (F4-1 CRITICAL, F4-2 CRITICAL)
identified that the §7 immunity claims for `audit_classified` and
`chameo:hasCalibration` are operationally undefined. The cross-persona
synthesis (above) raised this to "either operationalise or rewrite §7 with
a feature-toggle-off default."

**Specific escalation question for the doc author / Flo:** which path?

- **Path A (operationalise in v1):** add §"Introducing `audit_classified`"
  sub-section with attribute spec + migration + admin surface; add §"Calibration
  cert linkage today" using the temporary `calibration_cert_id` annotation
  key; seed at least one MFFD channel with a calibration cert; ADMIN-STALE-CH-c
  ships with both immunity gates working. Adds ~2 person-days to the
  ADMIN-STALE-CH-c estimate (4 d → 6 d).
- **Path B (defer with feature toggle):** rewrite §7 to "v1 ships with NO
  EN 9100 / FAIR immunity gates. The tool is gated behind a feature toggle
  `shepard.stale-channels.enabled=false` (default) — operators must
  explicitly opt-in and accept the risk. Once ONT1d and RDM1 ship, the
  immunity gates become operational and the feature defaults to ON."

Either is defensible. Path A is the higher-effort, higher-promise route;
Path B is the honest-MVP route. **Both personas (Role 4 + Role 5) ACCEPT
either path conditional on the doc making the choice explicit.** Without
the choice, the doc claims compliance it doesn't deliver, which is the
worst outcome.

### E3 — HARD-REQ-1 compatibility with Idempotency-Key (F3-3)

**Status:** Not raised by personas but surfaces from cross-persona
synthesis. The §7 HARD-REQ-1 re-verify pattern and the F3-3 Idempotency-Key
replay both interact: a retry of a delete that succeeded the first time
must replay the receipt (saying "these N deleted, these M refused") even
if the underlying race state has changed since. **Recommendation:** the
Idempotency-Key cache stores the receipt, not just the request; replays
return the stored receipt without re-running the delete. This is the
Stripe pattern.

---

## §7 Stage advancement recommendation

All four personas issue **ACCEPT-WITH-CHANGES** verdicts. Per
`feedback_persona_audit_triggers.md` §"Discipline rules", CRITICAL + MAJOR
findings **block** stage advancement until closed (or a deliberate
"won't-fix" rationale is captured).

**Critical findings count:** 3 (F4-1, F4-2, F5-1, F5-2 — F4-1+F5-1+F5-2
are the cross-persona "immunity is hand-waved" synthesis; F4-2 is the
calibration-cert sub-case).

**Major findings count:** 11.

**Recommendation:**

- Stage **STAYS at `feature-defined`** pending Round-1 changes (the cross-
  persona synthesis CRITICAL items + the F9-2 collision policy + the F3-5
  recover endpoint + the F9-3 background-job spec).
- Doc author commits Round-1 changes addressing at minimum the CRITICAL
  items (F4-1, F4-2, F5-1, F5-2) and the high-MAJOR items (F3-5, F9-2,
  F9-3, F3-3).
- Same four personas re-run for Round 2 (per the trigger matrix — same
  personas, scoped to their own filings) and sign off line-by-line.
- ONLY THEN stage advances to `audited-by-personas`.

**Alternative recommendation IF Path B (feature-toggle-off-by-default) is
chosen for E2:** the four personas can be invited to sign off on stage
advancement to `audited-by-personas` with the understanding that the
feature ships disabled-by-default and the immunity-gate-light shape is the
v1 promise. This unblocks the doc's progression without forcing premature
implementation of `audit_classified` / publication-state / DOI-immunity
gates.

---

## §8 Sources

**External (per `feedback_agents_use_research_tools.md` ≥3 per persona):**

Manufacturing-Quality:
- [BPRHub — AS9100D Record Retention: Key Requirements & Best Practices](https://www.bprhub.com/blogs/as9100d-record-retention)
- [Elsmar Quality Forum — Flow-down of Aerospace record retention requirements (AS9100)](https://elsmar.com/elsmarqualityforum/threads/flowdown-of-aerospace-record-retention-requirements-as9100.68185/)
- [Elsmar — AS9100 Records Retention Time Requirements](https://elsmar.com/elsmarqualityforum/threads/as9100-records-retention-time-requirements.59481/)
- EN 9130:2020 industry guidance (cited via BPRHub summary above)

RDM / FAIR:
- [DataCite — Best Practices for Tombstone Pages](https://support.datacite.org/docs/tombstone-pages)
- [DataCite — DOI Persistence](https://support.datacite.org/docs/doi-persistence)
- [ARDC — Deleting a DOI](https://documentation.ardc.edu.au/doi/deleting-a-doi)
- Welzmüller, F. et al. (2024) — "Research Data Management for Space Missions: Practical Experiences and Lessons Learned." [DLR eLib 215120](https://elib.dlr.de/215120/)

API Scrutinizer:
- [IETF RFC 9457 — Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/rfc9457/)
- [Redocly — RFC 9457: Better information for bad situations](https://redocly.com/blog/problem-details-9457)
- [Swagger — Problem Details (RFC 9457): Doing API Errors Well](https://swagger.io/blog/problem-details-rfc9457-doing-api-errors-well/)
- [Zalando — Provide guidelines on batch/bulk requests and 207 (issue #127)](https://github.com/zalando/restful-api-guidelines/issues/127)
- [SPS Commerce — Bulk Operations standard](https://spscommerce.github.io/sps-api-standards/standards/bulk.html)
- [OneUptime — How to Handle Partial Success in Bulk API Operations](https://oneuptime.com/blog/post/2026-02-02-rest-bulk-api-partial-success/view)

Reluctant Senior:
- [UX Movement — How to Make Sure Users Don't Accidentally Delete](https://uxmovement.com/buttons/how-to-make-sure-users-dont-accidentally-delete/)
- [LogRocket — Double-check user actions: All about warning message UI](https://blog.logrocket.com/ux-design/double-check-user-actions-confirmation-dialog/)
- [UX Psychology — How to design better destructive action modals](https://uxpsychology.substack.com/p/how-to-design-better-destructive)
- [Cultured Systems — Avoiding the soft delete anti-pattern](https://www.cultured.systems/2024/04/24/Soft-delete/)
- [Hacker News discussion — Avoiding the soft delete anti-pattern](https://news.ycombinator.com/item?id=40326815)
- [TimescaleDB GitHub #6196 — Deletion in compressed chunks led to database size increase of nearly 20x](https://github.com/timescale/timescaledb/issues/6196)
- [Tiger Data — 42× Faster DELETEs](https://www.tigerdata.com/blog/42x-faster-deletes-accelerating-analytics-and-high-volume-ingestion-with-timescaledb-2-21)

**Internal cross-references:**

- `aidocs/data/89-stale-channel-admin-design.md` (the audited doc)
- `aidocs/16-dispatcher-backlog.md` ADMIN-STALE-CH row
- `aidocs/platform/87-timeseries-appid-migration.md` (TS-IDa shepard_id baseline)
- `aidocs/integrations/67-unhide-publish-plugin.md` (harvest impact reference for F5-3)
- `aidocs/agent-findings/manufacturing-quality.md` (prior MQ findings — NCR / rework trace shape referenced in F4-4)
- `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java` (verified)
- `backend/src/main/java/de/dlr/shepard/context/references/timeseriesreference/model/ReferencedTimeseriesNodeEntity.java` (verified)
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql` (verified)
- `feedback_persona_audit_triggers.md` (trigger matrix this audit followed)
- `feedback_agents_argue_and_consult.md` (the discipline this audit follows)
- `feedback_agents_use_research_tools.md` (the research-tool requirement)
