---
stage: idea
last-stage-change: 2026-05-23
audience: contributor
---

# v5 OpenAPI — metadata enrichment survey for the MFFD importer (v15.10 candidates)

**Date:** 2026-05-23
**Backlog row closed:** `V5-METADATA-SURVEY` (`aidocs/16-dispatcher-backlog.md`)
**Source authority:** `backend/src/test/resources/fixtures/v5/openapi-5.4.0.json` (5.4.0 wire contract per `project_v5_legacy_source.md`)
**Importer under audit:** `examples/mffd-showcase/scripts/mffd-dropbox-import.py` (v14 banner, v15.x stream)

## Methodology + scope

Walked every path in `/shepard/api/...` (88 endpoints across 14 resource families).
Cross-referenced the importer's actual GET calls against the v5 surface to identify
endpoints it could call but doesn't, then categorized by enrichment value vs. friction.

**Live verification was NOT performed.** The cube3 source instance lives on the DLR
intranet (`backend.bt-au-cube3.intra.dlr.de`) which is unreachable from this dev box
per `feedback_host_boundary.md`. Schema fragments quoted below are from the v5.4.0
fixture; durable response-shape findings already captured on cube3 are recorded in
`project_mffd_api_keys.md` ("v5 API findings (durable)" block) and confirm the
fixture matches the wire reality (snake_case query params, no `X-Total-*` headers,
`/predecessors` 404 — body carries embedded ID lists instead).

## What I found

### The headline: free metadata is already on the wire and being dropped

The biggest enrichment opportunity is **not a new endpoint**. The importer already
fetches the DataObject body, the FileReference body, the TimeseriesReference body,
and the StructuredDataReference body — and every one of those wire responses
carries `createdBy`, `updatedBy`, `createdAt`, `updatedAt` (and, for DataObject:
`parentId`, `predecessorIds`, `successorIds`, `referenceIds`, `incomingIds`).

Today the importer drops all of them. The dest collection's prov chain therefore
shows the importer's API-key holder as `createdBy` on every migrated artefact,
with the import timestamp as `createdAt`. Source-side ground truth — "kreb_fl
created this DataObject on 2023-01-19" — is lost despite already being in the
HTTP responses the script parses.

Worse, the script's docstring (line 32–36) explicitly **claims** to preserve
source timestamps as `source_created` / `source_modified` attributes. The code
does not implement this. `mffd-dropbox-import.py:265–275` reads `created` /
`modified` into the `SourceDO` dataclass; `create_data_object` at line 458–477
never writes them to the dest body.

### Endpoints the importer doesn't call but could (12 candidates)

```
1.  GET /users/{username}                                  — identity + prov
2.  GET /users (= getCurrentUser, self-identity)           — alt to JWT decode
3.  GET /userGroups                                        — institutional taxonomy
4.  GET /collections/{id}/permissions                      — EN 9100 audit snapshot
5.  GET /collections/{id}/dataObjects/{id}/references      — references aggregator
6.  GET /collections/{id}/dataObjects/{id}/semanticAnnotations  — ontology links
7.  GET /collections/{id}/dataObjects/{id}/references/{id}/semanticAnnotations
8.  GET /collections/{id}/semanticAnnotations              — collection-level annotations
9.  GET /labJournalEntries?dataObjectId={id}               — narrative prov text
10. GET /timeseriesContainers/{id}/timeseries              — 5-tuple + TS semantic annotations
11. GET /versionz                                          — source-shepard version stamp
12. GET /[container]/{id}/permissions                      — per-container ACL snapshot
```

The task brief listed "Annotations (semantic + freetext)" under "what's already
extracted." That is incorrect — `grep semanticAnnotat\|labJournal` against
`mffd-dropbox-import.py` returns no matches. See §"What surprised me."

## Opportunities

Ranked by `value ÷ effort` for v15.10 inclusion.

| # | Opportunity | Endpoint(s) | Effort | Class |
|---|---|---|---|---|
| 1 | Preserve source `createdBy`/`updatedBy`/`createdAt`/`updatedAt` from already-fetched bodies | (none — local change) | S | **High-value, zero-friction** |
| 2 | Capture distinct `createdBy` users → enrich `:User` nodes on dest | `/users/{username}` per unique user (cached) | S | **High-value, low-friction** |
| 3 | Record source-instance version for prov | `/versionz` (1 call/run) | S | **High-value, low-friction** |
| 4 | Snapshot source permissions at import-time (EN 9100 + FAIR-A) | `/collections/{id}/permissions`, `/{container}/{id}/permissions` | M | **Medium-value (audit)** |
| 5 | Capture DataObject semantic annotations | `/collections/{id}/dataObjects/{id}/semanticAnnotations` | M | **Medium-value (FAIR-I)** |
| 6 | Capture lab journal entries | `/labJournalEntries?dataObjectId={id}` | M | **Conditional (depends on cube3 use)** |
| 7 | Capture timeseries 5-tuple + per-TS semantic annotations | `/timeseriesContainers/{id}/timeseries` | M | **Medium-value** |
| 8 | Mirror UserGroup membership → enrich collaborator graph | `/userGroups`, `/userGroups/{id}` | M | **Medium-value** |
| 9 | Mirror DataObject parent/predecessor chain via `parentId` + `predecessorIds` (already on wire) | (none — local change) | S | **High-value, partially done** |
| 10 | Subscriptions of source user | `/users/{u}/subscriptions` | S | **Low-value, reject** |
| 11 | API keys of source user | `/users/{u}/apikeys` | S | **Low-value + sensitive, reject** |
| 12 | Container `oid` (Mongo collection id) | (none — already on wire, FileContainer.oid + StructuredDataContainer.oid) | S | **Low-value, internal substrate id** |

## Recommended for v15.10

Ordered by ship-priority. Items 1–3 are essentially "fold these into the next PR
that already touches the importer."

| Rank | Recommendation | Endpoint | Effort | Per-DO GET cost added | Lens |
|---|---|---|---|---|---|
| 1 | **Land the docstring promise.** Read `createdBy`, `updatedBy`, `createdAt`, `updatedAt` from the DataObject body the importer already fetches; write them as `source_created_by`, `source_updated_by`, `source_created`, `source_updated` attributes on each dest DO. Same for FileReference/TSReference/StructuredReference. **Zero new GETs.** | (none) | S | **0** | RDM (FAIR-R), Manufacturing (EN 9100), API Scrutinizer (free) |
| 2 | **Distinct-creator user resolution.** Maintain an in-memory `dict[username, UserDTO]`. For each DataObject, if `createdBy` not yet resolved, call `GET /users/{username}` once and cache. Forward `firstName`/`lastName`/`email` as `source_created_by_displayName` / `_email` attrs on the DO. (Generalises MFFD-IMPORT-USER-CAPTURE which only resolves the API-key holder.) Also create/update a `:User` node on dest if `auth.users.mirror.enabled` is on. | `GET /users/{username}` | S | **0 per DO** (amortized: ~3–5 GETs total for an MFFD run) | RDM (FAIR-R, collaborator graph), Manufacturing (responsible person trail) |
| 3 | **Source-version provenance.** One `GET /versionz` per run; persist as `source_shepard_version` attribute on the destination collection (`PATCH /v2/collections/{appId}`). | `GET /versionz` | S | **0 per DO** (1 GET total) | API Scrutinizer (cheap), RDM (reproducibility) |
| 4 | **Source-side parent + predecessor chain.** The DataObject body already carries `parentId` + `predecessorIds`. The importer already calls `_link_predecessor` for explicit predecessor wiring it computes itself, but does not honour the source's own chain. Plumb both through. | (none — already on wire) | S→M | **0** | Data Ontologist (provenance integrity), RDM (FAIR-R) |
| 5 | **Permissions snapshot at-time-of-import.** For each source collection + container, `GET /[resource]/{id}/permissions` once and write the entire `Permissions` object as a JSON-stringified `source_permissions_at_import` attribute on the dest collection (and per-container as well if mirroring containers 1:1). Auditor reproducibility for EN 9100 §7.5 + §8.4. Gate behind `--enrich=permissions`. | `GET /collections/{id}/permissions`, `GET /{container}/{id}/permissions` | M | **+1 per collection + per container** (not per DO) | Manufacturing/Quality (audit), RDM (FAIR-A) |

## Backlog rows to file (for `aidocs/16-dispatcher-backlog.md`)

Paste-ready (matching the existing V5-METADATA-SURVEY row format).

```markdown
| MFFD-IMPORT-SOURCE-BY-ATTRS | **v15.10 — preserve source createdBy/updatedBy/createdAt/updatedAt on every migrated artefact.** The importer already fetches these via the DataObject/FileRef/TSRef/StructuredRef body but drops them; the docstring at `mffd-dropbox-import.py:32–36` claims preservation that the code does not implement. Land as `source_created_by`, `source_updated_by`, `source_created`, `source_updated` attributes. Zero new GETs — fold the field copy into the existing `create_data_object` / `upload_file` flows. Matched per-payload-type: also surface on TimeseriesReference + StructuredDataReference + FileReference create bodies. | S | queued | Source: V5-METADATA-SURVEY 2026-05-23. Bug-shaped — docstring claims feature, code drops it. Sibling to MFFD-IMPORT-USER-CAPTURE; ship together. |

| MFFD-IMPORT-DISTINCT-USERS | **v15.10 — resolve every distinct `createdBy`/`updatedBy` on source, not just the API-key holder.** Generalises MFFD-IMPORT-USER-CAPTURE: cache `dict[username, UserDTO]` per-run; on each DataObject if username not yet resolved, call `GET /users/{username}` once. Forward `firstName`/`lastName`/`email` as `source_created_by_displayName` + `_email` attrs. Optionally mint `:User` mirror nodes on dest with `prov:wasDerivedFrom` source-instance edge. Amortized cost: 3–5 user-GETs per MFFD run, not per DataObject. | S | queued | Source: V5-METADATA-SURVEY 2026-05-23. Supersedes the narrow scope of MFFD-IMPORT-USER-CAPTURE (which only captures the runner's own identity). |

| MFFD-IMPORT-VERSIONZ-STAMP | **v15.10 — stamp source-instance version on every dest collection.** One `GET /versionz` per run; write `source_shepard_version` attr to the dest collection. Closes the "auditor doesn't know which shepard version the source ran" gap. Trivial — one extra GET total per import. | S | queued | Source: V5-METADATA-SURVEY 2026-05-23. Pairs with import-session attrs already on dest. |

| MFFD-IMPORT-SOURCE-CHAIN | **v15.10 — honour the source-side parent + predecessor chain.** Source DataObject body already carries `parentId` + `predecessorIds`. The importer currently re-derives predecessors from filename ordering and ignores `parentId` entirely. Plumb source IDs through name-resolution (source ID → dest appId) so the dest DAG matches source-side topology exactly. | M | queued | Source: V5-METADATA-SURVEY 2026-05-23. Provenance integrity gap — current dest chain may diverge from source chain. |

| MFFD-IMPORT-PERMS-SNAPSHOT | **v15.10 / v15.11 — capture source-side permissions at-time-of-import for EN 9100 audit replay.** For each source collection and each source container, `GET /[resource]/{id}/permissions` once and persist as JSON-stringified `source_permissions_at_import` attribute. Gate behind `--enrich=permissions` (default off) — adds +1 GET per collection + per container. Auditor can replay "who could see this when import ran" without needing source-instance access. | M | queued | Source: V5-METADATA-SURVEY 2026-05-23. EN 9100 §7.5 + §8.4 trail. Manufacturing/Quality lens. |

| MFFD-IMPORT-SEMANTIC-ANNS | **v15.10 / v15.11 — pull semantic annotations from source and forward to dest.** Surprising gap: the importer claims to extract "Annotations (semantic + freetext)" but `grep semanticAnnotat` against `mffd-dropbox-import.py` returns zero matches. For each source DataObject: `GET /collections/{id}/dataObjects/{id}/semanticAnnotations`; for each TS: `GET /timeseriesContainers/{id}/timeseries/{tsId}/semanticAnnotations`. Mint matching annotations on dest. Subtle: requires `SemanticRepository` resolution — the propertyRepositoryId/valueRepositoryId on source must be re-pointed to dest-side repositories with the same endpoint URLs. | L | queued | Source: V5-METADATA-SURVEY 2026-05-23. FAIR-I gap. Cross-instance ontology resolution is the complication — design first. |

| MFFD-IMPORT-LABJOURNAL | **v15.10 / v15.11 — pull lab journal entries per DataObject from source.** `GET /labJournalEntries?dataObjectId={id}` per DataObject. Each LabJournalEntry has `journalContent` (markdown narrative) + `createdBy` + `createdAt`. Forward as 1:1 lab-journal-entry creations on dest. Conditional: only worth the +1 GET/DO if MFFD actually uses lab journals on cube3 — confirm with operator before scheduling. | M | queued | Source: V5-METADATA-SURVEY 2026-05-23. Pure prov-narrative loss if MFFD uses it. |

| MFFD-IMPORT-TS-FIVETUPLE | **v15.10 / v15.11 — pull source-side timeseries 5-tuple + per-TS semantic annotations.** Today the importer migrates timeseries via WIDE CSV export/import, which loses the source-side 5-tuple metadata (`measurement` / `device` / `location` / `symbolicName` / `field`) and any per-TS semantic annotations. Call `GET /timeseriesContainers/{id}/timeseries` (lists TS definitions) and `.../timeseries/{id}/semanticAnnotations` (per-TS ontology hooks). Forward both as TS attrs + annotations on dest. | L | queued | Source: V5-METADATA-SURVEY 2026-05-23. Pairs with the TS-ID migration (`aidocs/platform/87`). Domain-knowledge loss otherwise. |

| MFFD-IMPORT-USERGROUPS | **v15.10 / v15.11 — mirror UserGroup membership for the source user.** `GET /userGroups` once, filter to groups whose `usernames` includes the source API-key holder. Persist as `source_user_groups` attribute on the dest collection (or mint `:UserGroup` nodes on dest). Enables institutional-affiliation provenance. | M | queued | Source: V5-METADATA-SURVEY 2026-05-23. Pairs with MFFD-IMPORT-DISTINCT-USERS. |

| MFFD-IMPORT-CURRENTUSER-NORM | **v15.10 — self-identity fallback.** v5 has `GET /users` (= `getCurrentUser` per openapi-5.4.0.json) which returns the User object for the API-key holder. Use this as the canonical self-identity check instead of JWT decode — works without parsing the JWT and is symmetric with v2 `/v2/users/me`. | S | queued | Source: V5-METADATA-SURVEY 2026-05-23. Importer already attempts `/users/currentUser` (line 222) — that path is wrong; correct endpoint is just `GET /users`. |
```

## Lens panel

**API Scrutinizer.** Every GET endpoint listed is idempotent (read-only) and safe
to call N times. The 5.4.0 wire contract does not document rate-limits; the only
concern is sustained-load amplification. Today the importer issues 4 GETs per
DataObject (body + 3 ref-list endpoints). Adding "permissions" raises it to 5;
adding "semantic annotations" to 6; "lab journals" to 7. With `--workers 4`
sustained that means cube3 sees up to 75% more inbound GETs at peak — material,
not catastrophic. The right shape is **a default-off `--enrich={base,full,
audit}` flag with per-enrichment opt-in**, not an unconditional fat fetch.
Distinct-user resolution is amortized (capped at ~5 GETs/run regardless of DO
count) so it's safe to default on. Self-identity via `GET /users` (not
`/users/currentUser` as the importer currently tries on line 222) is one
correction worth landing in the same PR.

**Research Data Manager.** Recommendations 1, 2, and 4 close concrete FAIR gaps.
Today the dest's `createdBy` on every migrated artefact is the importer's
API-key holder — which is **wrong** under FAIR-R (Reusable): a downstream
researcher reading the dest cannot tell who actually authored the source
DataObject. Recommendation 1 fixes this with zero new GETs. Recommendation 2
extends it to a full collaborator graph (every distinct `createdBy` becomes a
typed `:User` node on dest with displayName + email) — the FAIR-R "context-rich
prov beyond just user identity" the V5-METADATA-SURVEY brief asked for.
Recommendation 4 (permissions snapshot) is FAIR-A: an auditor can verify "who
had access at time-of-import" without source-side re-auth, which closes the
"source instance went away" case that the PLUTO RDM paper (Welzmüller et al.
2024) calls out as the dominant long-tail risk. Semantic annotations and lab
journals are FAIR-I gaps; they're medium-priority because they're conditional
on cube3 actually using those primitives for MFFD (which we cannot verify from
this host).

**Reluctant Senior.** Operator's mental model when v15.10 begins pulling new
endpoints: "is this going to hammer cube3 while I'm trying to run a real test?"
Quantify it for them up-front. Today: 4 GETs/DO. v15.10 *with the recommended
defaults* (items 1+2+3 only): still ~4 GETs/DO — items 1 and 3 cost zero per-DO
load, item 2 amortizes to ~5 total user-GETs per run. **No worker-load increase
visible to cube3.** Only when the operator opts into `--enrich=permissions` or
`--enrich=annotations` does GETs/DO go up; the help text should print the
expected load multiplier next to the flag (`--enrich=permissions (+1 GET per
container, ~20% increase)`). This satisfies "operator doesn't get surprised by
5× the GETs." Senior researcher reads the `--help` and knows what they're
signing up for.

**Industrial Manufacturing & Quality Engineer.** Items 1, 2, 4 (and 5 for
ontology-traceable inspections) build the EN 9100 audit trail on the dest side
that today's import cannot reconstruct. The audit question is: "show me which
operator created this NCR record and who could see it when it was filed."
Item 1 closes the "who created it" half (today: lost). Item 2 closes the
"who is that person, what's their institute affiliation, where do I email them"
half — vital for `Concession` records under DIN EN 9100 §8.7 where an auditor
needs a callable point-of-contact 5 years post-fact. Item 4 (permissions
snapshot) closes the "who could see it when" half. The lab journal opportunity
(item 6) is the EN 9100 §7.5.3 narrative trail. Together these promote dest
artefacts from "data we ingested" to "FAIR-trail with personnel + access
provenance" — the difference between "this was a fun import" and "this is the
authoritative record." Worth noting that this aligns with the
`OBS-MFFD1`-style "shepard measures itself" principle in `MEMORY.md`: the
import-time permissions + creator snapshot is a self-observability act
captured inside shepard's own TS/attr substrate.

## Ideas

- **Per-enrichment flag matrix.** `--enrich={base,users,perms,annotations,
  journals,full}` (comma-separated, default `base` = zero net GET increase).
  Print expected GET amplification in `--help` next to each.
- **Reusable as a v2 endpoint.** Once `MFFD-IMPORT-SOURCE-BY-ATTRS` lands, the
  same body-field-preservation logic could ship as a v2 `/v2/import/v5-shepard`
  source-adapter — generalising the MFFD-specific script into a first-class
  cross-instance import primitive (sibling to the L2d work). The
  `shepard-plugin-importer` plugin design seed (`project_importer_plugin.md`)
  is the natural home.
- **m4i alignment.** `source_created_by` etc. should ultimately become typed
  m4i / PROV-O edges (`prov:wasAttributedTo`, `prov:wasGeneratedBy`) once
  `M4I-b` ships (predicate canonicalization — `MEMORY.md`). Today they're
  strings on the attr map; under M4I-b they get typed individuals.

## Real-world impact

- **MFFD-Dropbox today:** 100% of ingested DataObjects show `createdBy =
  <importer-api-key-holder>` on dest. Dest cannot answer "who actually
  authored TR-Q1-step5 at cube3" without operator-side knowledge. v15.10
  item 1 fixes this for **every artefact already migrated** (a one-shot
  back-fill pass over the dest is feasible — fetch source body, patch dest
  attrs).
- **EN 9100 audit posture:** today dest cannot pass an audit asking "produce
  the access-control state of this collection at the time of import." With
  item 4 it can.
- **FAIR score (PLUTO + MFFD):** F=2/3 today (no PID), A=2/3 today (auth but
  no historical ACL snapshot), I=1/3 today (no annotations replicated), R=1/3
  today (createdBy lost). v15.10 items 1-3 nudge R to 2/3 immediately;
  4+5+6 push A to 3/3 and I to 2/3.

## Gaps & blockers

- **Cannot live-verify against cube3 from this host.** Schema fragments cited
  from openapi-5.4.0.json fixture; durable cube3 findings already in
  `project_mffd_api_keys.md`. Recommend the user spot-check one response per
  category (`/users/kreb_fl`, `/versionz`, `/collections/48297/permissions`)
  on the next DLR-machine session before v15.10 implementation starts.
- **Cross-instance semantic-repository resolution** is the hard blocker for
  `MFFD-IMPORT-SEMANTIC-ANNS`. Source-side `SemanticAnnotation` carries
  `propertyRepositoryId` + `valueRepositoryId` — those are LONG IDs scoped to
  the source instance. Dest needs equivalent repositories (matched by
  endpoint URL, not by ID). Design needed first; coding second.
- **No way to know if MFFD uses lab journals on cube3** without live access;
  decide lab-journal scope before v15.10 work starts.
- **`source_created` / `source_modified` mismatch:** v5 body uses
  `createdAt` / `updatedAt`; the importer dataclass at line 273–274 has
  fallback shapes (`creationDate` / `modificationDate`) that look like
  upstream-fork-drift compatibility. Worth confirming which fork the cube3
  source actually serves before deciding the canonical wire key.

## What surprised me

- **The biggest enrichment is already on the wire.** I expected to recommend
  new GET endpoints; the headline turned out to be "use the fields the
  responses already carry." The importer drops at least 4 prov-critical
  fields per DataObject body.
- **Docstring promises a feature the code doesn't implement.** Lines 32–36
  claim `source_created` / `source_modified` are preserved. They're not. This
  is a bug-shaped finding, not a feature request.
- **Task brief said "Annotations (semantic + freetext)" are already
  extracted.** They're not — `grep semanticAnnotat\|labJournal` returns zero
  matches in the importer. The task brief either pre-states intent or was
  written against a different importer version. Operator should confirm
  before MFFD-IMPORT-SEMANTIC-ANNS is scoped.
- **`/users` (no path arg) = `getCurrentUser`**, not "list all users." Easy
  to miss — the importer at line 222 calls `/users/currentUser` (a path that
  does not exist in 5.4.0) and falls through. One-character fix.
- **The DataObject body already carries `predecessorIds` + `successorIds` +
  `parentId`** — yet the importer rebuilds the chain from filename ordering
  in MFFD-specific logic. Honouring the source chain instead would be both
  more correct and less code.
- **Container `oid` is exposed** on FileContainer + StructuredDataContainer
  (the Mongo collection identifier). Internal substrate ID leak from the v5
  surface — out of scope here but worth a future API-Scrutinizer row.

---

## Executive summary

- **Biggest win:** Item 1 — preserve source `createdBy`/`updatedBy`/`createdAt`/
  `updatedAt` on every artefact. Zero new GETs, fixes a documented-but-broken
  promise, raises FAIR-R immediately.
- **Biggest risk:** `MFFD-IMPORT-SEMANTIC-ANNS` (cross-instance repository
  resolution) — needs a design pass before any v15.10 coding, otherwise the
  attempt fails confusingly with mismatched LONG IDs.
- **Next concrete step:** Land `MFFD-IMPORT-SOURCE-BY-ATTRS` +
  `MFFD-IMPORT-DISTINCT-USERS` + `MFFD-IMPORT-VERSIONZ-STAMP` together as
  v15.10 (all S-effort, zero per-DO GET amplification). Defer items 4–10 to
  v15.11+ with `--enrich=` opt-in.
