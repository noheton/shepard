---
name: Project entity + sub-Collection registry (urn:shepard:partOf, non-exclusive)
description: Project-as-registry pattern — an ordinary Collection with a urn:shepard:project role annotation that lets sibling Collections declare it as a parent (non-exclusively — a Collection can belong to multiple Projects). The funder/internal Programme is a text-typed urn:shepard:programme annotation on the Project itself, not a separate entity.
type: design
stage: tests-implemented
last-stage-change: 2026-06-02
---

# 121 — Project entity + sub-Collection registry

**Status:** decision-quality 2026-06-02. Revised 2026-06-02 evening after operator
note: *"projects can collect/bundle collections non-exclusively"* + *"programs
exist but are part of project metadata within DLR"*.
**Companion:** `aidocs/integrations/119-mffd-collection-layout.md` (MFFD-specific
application), `aidocs/integrations/118-mffd-process-chain-mapping.md` (cross-
Collection Predecessor edges).
**Replaces option D** (native `parentCollectionAppId` field) as the chosen
shape: this design adds no schema change, only annotation predicates +
endpoint + UI panel.

## 0. TL;DR

A **Project** is an ordinary `Collection` with a `urn:shepard:project = true`
role annotation. Sibling Collections declare it as a parent with
`urn:shepard:partOf = <project.appId>` — and a Collection can declare
**multiple** Projects this way (non-exclusive bundling).

The DLR **programme** (Clean Aviation JU, DLR Project Line 4, Horizon Europe
strand, etc.) is a *literal-text* annotation on the Project itself:
`urn:shepard:programme = "Clean Aviation JU"`. Programmes are not separate
Shepard entities — they are Project metadata.

A new endpoint `GET /v2/collections/{appId}/sub-collections` resolves the
inverse of `partOf`. A new panel on the Collection-detail page renders the
children as tiles. A new top-nav entry `/projects` lists every Collection
that has `urn:shepard:project = true` — the operator's **one entrypoint to
multi-step efforts** like MFFD, PLUTO, LUMEN.

Zero schema change. Six small code changes (one Cypher migration to add the
new predicates to the controlled vocabulary; one backend endpoint; one
frontend panel; one frontend top-nav entry; one Collection-card badge; one
SHACL shape).

## 1. Why this shape (vs the alternatives)

I considered five shapes (per the 2026-06-02 chat):

| | Option A: B-layout + custom `/mffd` route | **Option B: this (project-as-registry)** | Option C: one big Collection | Option D: native `parentCollectionAppId` | Option E: TPL6 saved views |
|---|---|---|---|---|---|
| Data-model change | none | none (annotation only) | none | nullable column on `Collection` | none |
| Generalises beyond MFFD | no (`/mffd` is bespoke) | **yes** | no | yes | yes |
| One operator entrypoint | yes (custom route) | **yes** (Project tile + sub-collection panel) | yes (the Collection) | yes (parent in tree) | partly (saved view discoverability is poor) |
| Non-exclusive bundling | yes | **yes** (multi-valued `partOf`) | no | no (single-parent only) | yes (Collection in many views) |
| Per-step ACL / DOI / SHACL | yes (6 Collections) | **yes** (6 Collections) | no (collapses) | yes | yes |
| Scale fixes still in play | yes | **yes** | no (regresses) | yes | yes |
| Engineering cost | 1d (UI only) | **1d** | 0 (no work) | 2–3d + Neo4j migration + v1-compat carve-out | 0.5d (label only) |
| Maintenance debt | medium (per-programme route) | **low** | high (scale defects) | medium (schema migration; single-parent restricts use) | low |

Option B wins on the row that matters most: **same engineering cost as a
one-off custom route, but it generalises to every future project** (PLUTO,
LUMEN, BT-KVS, …) without adding more code — *and* supports the non-exclusive
membership that funder reporting + cross-cutting initiatives require.

## 2. The annotation contract

Three new predicates added to the controlled vocabulary
(V##__add_project_predicates.cypher):

```
urn:shepard:project                 role marker on a Collection that says
                                    "this Collection is a Project — it expects
                                    child Collections via urn:shepard:partOf".
                                    Value = literal "true".
                                    Cardinality: 0..1 per Collection.

urn:shepard:partOf                  on any Collection, says "this Collection is
                                    a child of <other Collection appId>".
                                    Value = a Collection appId (UUID v7).
                                    Cardinality: 0..N per Collection — a
                                    Collection can belong to multiple Projects.
                                    A Collection can even be itself a Project
                                    AND a child of another Project (Projects
                                    can nest — see §10 item 3).

urn:shepard:programme               on a Project Collection, names the funding /
                                    DLR-internal programme line. Free-text.
                                    Cardinality: 0..N (a Project can be funded
                                    or accounted under multiple programmes).
                                    Examples:
                                      "Clean Aviation JU"
                                      "DLR Project Line 4 (Composites)"
                                      "Horizon Europe — Cluster 5"
                                    Not a separate Shepard entity per operator
                                    decision 2026-06-02 — these are Project
                                    metadata strings, queryable via SPARQL but
                                    not first-class.
```

**SHACL constraints:**

- `urn:shepard:project` value must be `"true"` (boolean literal).
- The target of `urn:shepard:partOf` must itself carry `urn:shepard:project = true`
  (enforced at write time — prevents pointing a child Collection at another
  non-Project Collection). Multiple `partOf` annotations are allowed.
- `urn:shepard:programme` only valid on a Collection that has
  `urn:shepard:project = true` (Programme is Project metadata, not Collection
  metadata in general).

**Owner-write only:** mutating any of the three predicates requires the
`instance-admin` role OR ownership of *both* the parent Project and the child
Collection. Enforced in `SemanticAnnotationService.write()`.

## 3. Backend — generic `/v2/projects/` REST surface

Projects get their **own dedicated REST namespace** at `/v2/projects/{appId}`
— not a one-off sub-resource on `/v2/collections/`. Even though a Project is
*implemented* as a Collection carrying `urn:shepard:project = true`, callers
should be able to talk to it through a clean Project-shaped API without
threading Collection-semantics through. This keeps Project-aware behaviour
discoverable in the OpenAPI surface and lets future Project features
(programme metadata, cross-Collection roll-ups, REP export, …) land on
that namespace rather than accreting on `/v2/collections/`.

`{appId}` is the Project's Collection appId (UUID v7). Requests against a
non-Project Collection appId return **404** uniformly across the
sub-resources — `/v2/projects/` only addresses Collections that carry
`urn:shepard:project = true`.

### 3.1 `GET /v2/projects/{appId}` — Project resource

```
Path  : /v2/projects/{appId}
Method: GET
Auth  : same as /v2/collections/{appId} (read)

Response 200:
  {
    "appId": "...",
    "id": 42,
    "name": "MFFD",
    "heroImage": "...",
    "synopsis": "...",
    "ownerGroup": "...",
    "programmes": ["Clean Aviation JU", "DLR Project Line 4"],
    "subCollectionCount": 6,
    "aggregateDoCount": 17324,
    "lastActivity": "2026-06-02T18:42:00Z",
    "isProject": true   // always true here; included for cross-API parity
  }

404: when the Collection at {appId} is not a Project
     (does not carry urn:shepard:project = true).
```

The shape is *Project-flavoured* — it adds `programmes`,
`subCollectionCount`, `aggregateDoCount` to the base Collection fields and
drops Collection-only internals (DataObject pagination cursors, container
references, …). Callers wanting the raw Collection shape stay on
`/v2/collections/{appId}`.

### 3.2 `GET /v2/projects/{appId}/sub-collections`

(Was `GET /v2/collections/{appId}/sub-collections` in the original draft;
moved here because it is the canonical Project navigation resource. A thin
forwarder remains at the Collection path during the deprecation window —
see §3.5.)

```
Path  : /v2/projects/{appId}/sub-collections
Method: GET
Auth  : same as /v2/collections/{appId} (read)
Params:
  ?include=full           default: trimmed shape (id, appId, name, hero, doCount, lastActivity)
                          full: same shape as /v2/collections/{appId} response

Response 200:
  {
    "projectAppId": "<this project appId>",
    "programmes": ["Clean Aviation JU", "DLR Project Line 4"],   // urn:shepard:programme values
    "subCollections": [
      {
        "appId": "...", "id": ..., "name": "...", "heroImage": "...",
        "doCount": 8251, "lastActivity": "...", "ownerGroup": "...",
        "alsoMemberOf": ["<other-project-appId>", ...]            // when the child has multiple urn:shepard:partOf
      },
      ...
    ]
  }
```

**Cypher (shape):**

```cypher
MATCH (c:Collection {appId: $parentAppId})
OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(proj:SemanticAnnotation {predicate: 'urn:shepard:project'})
OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(prog:SemanticAnnotation {predicate: 'urn:shepard:programme'})
OPTIONAL MATCH (child:Collection)-[:HAS_SEMANTIC_ANNOTATION]->(p:SemanticAnnotation {predicate: 'urn:shepard:partOf', value: $parentAppId})
WHERE child <> c
OPTIONAL MATCH (child)-[:HAS_SEMANTIC_ANNOTATION]->(also:SemanticAnnotation {predicate: 'urn:shepard:partOf'})
WHERE also.value <> $parentAppId
RETURN c.appId         AS parentAppId,
       (proj IS NOT NULL) AS parentIsProject,
       collect(DISTINCT prog.value) AS programmes,
       collect({child: child, alsoMemberOf: collect(DISTINCT also.value)}) AS subCollections
```

`ProjectsRest` resource → `ProjectsService` → `ProjectsDAO`.
Returns 404 when `{appId}` is not a Project. Returns 200 with empty
`subCollections: []` when the Project has no children.

### 3.3 `GET /v2/projects/{appId}/by-annotation/{predicate}/{value}`

Generic cross-Collection roll-up: walk the Project's
`urn:shepard:partOf` children and return every DataObject across them whose
annotation `{predicate} = {value}` is set — directly on the DO or inherited
via a parent walk.

```
Path  : /v2/projects/{appId}/by-annotation/{predicate}/{value}
        {predicate} URL-encoded (e.g. urn%3Ashepard%3Amffd%3Alayer)
Method: GET
Auth  : read permission on the Project Collection
Params:
  ?inherit=true            default: include DOs that inherit the annotation from a parent DO
  ?include=identity        default: identity-only shape (appId, id, name, kind, collectionAppId)
                           ?include=annotations adds the matched annotation values
  ?page=N&pageSize=K       standard pagination (k≤500)

Response 200:
  {
    "projectAppId": "...",
    "predicate": "urn:shepard:mffd:layer",
    "value": "18",
    "totalCount": 174,
    "page": 1, "pageSize": 100,
    "results": [
      {
        "appId": "...", "id": ..., "name": "Run_S2_M3_L18_F4_R1",
        "kind": "DataObject",
        "collectionAppId": "...",     // which Collection in the partOf set holds it
        "collectionName": "mffd-afp-tapelaying",
        "matchedAnnotations": [        // only when ?include=annotations
          {"predicate": "urn:shepard:mffd:layer", "value": "18", "source": "inherited", "fromAppId": "..."}
        ]
      },
      ...
    ]
  }

404: when {appId} is not a Project
422: when {predicate} is unknown to the SemanticVocabularyProvider
```

This is the **single generic surface** that consumers — UIs, MCP tools,
notebook scripts, the VIEW_RECIPE renderer — use to compose "all data
across a Project keyed by one annotation". MFFD per-Layer, PLUTO
per-mission-phase, LUMEN per-test-bench, BT-KVS per-LRU-batch all
resolve through this endpoint with their domain's predicate. No
domain-specific routes accreted.

### 3.4 No new write endpoints

The Project-marking annotations (`urn:shepard:project`, `urn:shepard:partOf`,
`urn:shepard:programme`) are written via the existing
`POST /v2/collections/{appId}/annotations` endpoint, gated by the §2 SHACL
constraints. No new write surface.

### 3.5 v1 compatibility + transitional aliases

`/shepard/api/...` endpoints (upstream-frozen surface) are untouched.

For the prior draft that placed sub-collections on
`/v2/collections/{appId}/sub-collections`: a thin forwarder may be added at
that path during the SHACL-substrate deprecation window, 301-redirecting
to `/v2/projects/{appId}/sub-collections`. It is acceptable to skip the
forwarder entirely if no production caller has bound to the old path yet
(check the access logs before deciding). The PR landing PROJ-REST-2
should make this call.

Per CLAUDE.md §API-version-policy, `/v2/projects/` is additive — `/v2/`
is the development surface where new resources land.

### 3.6 MCP coverage

Two new MCP tools, both Project-shaped:

- `getProject({projectAppId})` → `/v2/projects/{appId}` shape
- `getProjectSubCollections({projectAppId})` → `/v2/projects/{appId}/sub-collections`
- `queryProjectByAnnotation({projectAppId, predicate, value, inherit?, page?, pageSize?})`
  → `/v2/projects/{appId}/by-annotation/{predicate}/{value}` shape

Lets Claude / agent clients walk a Project's structure, fetch its
programme metadata, and pull cross-Collection annotation roll-ups without
hand-rolling SemanticAnnotation queries.

## 4. Frontend — three surfaces

### 4.1 Sub-collections panel on Collection-detail (`CollectionSubCollectionsPanel.vue`)

Mounted on `pages/collections/[collectionId]/index.vue`. Renders a tile grid
when `GET /v2/collections/{appId}/sub-collections` returns ≥1 child. Each tile:

```
┌──────────────────────────────────┐
│ <heroImage>                       │
│                                   │
│ MFFD — AFP Tapelaying             │
│ 8 251 DOs · last activity 5 min ago│
│ owned by mffd-afp-team            │
│ also: Clean Aviation Composites   │  ← when alsoMemberOf is non-empty
└──────────────────────────────────┘
```

Click → navigate to the child Collection's detail page (same route shape).
The panel hides itself entirely when there are no children — so this is
zero UI cost for Collections that aren't Projects.

For a Project Collection that has children, the panel also surfaces the
**Programme strip** (read from the response's `programmes` array) above the
tiles — small chips like *Clean Aviation JU* + *DLR Project Line 4*. Click a
chip → search for all Projects with that programme value.

### 4.2 Top-nav route `/projects` (`pages/projects/index.vue`)

A new top-level nav entry between `/collections` and `/containers`. Lists
every Collection that carries `urn:shepard:project = true`. Each row:

- name + hero image
- programme chips (from `urn:shepard:programme` values)
- child-Collection count
- aggregate DO count across children
- last activity (max of children)
- owner group

Click → that Project's Collection-detail page (which renders the
sub-collections panel from §4.1).

A side filter on `/projects` lets the operator narrow by programme value
(exact match on the literal text). Implemented client-side at first; backend
filter becomes a follow-up when the project count grows.

### 4.3 Project badge + "member of" chips on Collection cards (`CollectionList.vue`)

When a Collection has `urn:shepard:project = true`, render a small chip
("Project · 6 sub-collections · *Clean Aviation JU*") on its row in the
master Collections list. Lets users discover the project structure from the
standard browse path, not just `/projects`.

When a Collection has `urn:shepard:partOf = <X>` (one or more values),
render a ghost chip group ("member of *MFFD Project* + 1 more") that links
to its parents — so a user landing on `mffd-afp-tapelaying` from a search
result sees that it's part of one or more larger Projects and can navigate
up.

## 5. SHACL shapes

Added to `backend/src/main/resources/shacl/project.shape.ttl`:

```turtle
shepard:ProjectShape
    a sh:NodeShape ;
    sh:targetClass shepard:Collection ;
    sh:property [
        sh:path shepard:project ;
        sh:datatype xsd:boolean ;
        sh:minCount 0 ;
        sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shepard:partOf ;
        sh:datatype xsd:string ;
        sh:minCount 0 ;
        # No maxCount — non-exclusive bundling.
        sh:sparql [
            sh:select """
                SELECT $this WHERE {
                    $this shepard:partOf ?parent .
                    FILTER NOT EXISTS { ?parent shepard:project true }
                }
            """ ;
            sh:message "urn:shepard:partOf target must itself be a Project" ;
        ]
    ] ;
    sh:property [
        sh:path shepard:programme ;
        sh:datatype xsd:string ;
        sh:minCount 0 ;
        # No maxCount — a Project can be funded by multiple programmes.
        sh:sparql [
            sh:select """
                SELECT $this WHERE {
                    $this shepard:programme ?p .
                    FILTER NOT EXISTS { $this shepard:project true }
                }
            """ ;
            sh:message "urn:shepard:programme is only valid on a Project Collection" ;
        ]
    ] .
```

A Cypher migration (`V##__seed_project_predicates.cypher`) adds all three
predicates to the `urn:shepard:*` controlled-vocab registry so they appear
in the admin vocabulary picker.

## 6. CLAUDE.md compliance audit

- **§ship a UI stub for every backend feature**: ✅ §4.1 (panel) + §4.2 (route) + §4.3 (badge) — three UI surfaces.
- **§top-nav reachable before beta**: ✅ §4.2 — the `/projects` route is in the top navbar.
- **§tools in-context first**: ✅ §4.1 is the primary entry (the Project tile on its Collection-detail page); `/projects` is the fallback global list.
- **§every reference type ships full CREDL**: not applicable — no new reference type; just three new predicates on existing entity.
- **§API-version policy**: ✅ §3 — new endpoint lives at `/v2/`.
- **§singleton FileReference**: not applicable.
- **§UI never asks for paths/URLs**: ✅ — parent Collection is picked by appId via a Collection picker, not by typing a URL.
- **§schema changes additive + nullable**: ✅ — no schema change at all.
- **§secondary writes fire-and-forget**: ✅ — annotations write via existing service.
- **§three-audience docs**: covered by this design doc + the admin runbook
  (TBD: how an admin marks a Collection as a Project + adds child Collections
  + sets programme metadata).

## 7. Backlog rows added by this doc

To `aidocs/16-dispatcher-backlog.md`:

| ID | Subject | Size | Status |
|---|---|---|---|
| `PROJ-PREDICATES-1` | Cypher migration: add `urn:shepard:project` + `urn:shepard:partOf` + `urn:shepard:programme` to controlled vocab + SHACL shape | S | queued |
| `PROJ-REST-1` | Backend `GET /v2/collections/{appId}/sub-collections` endpoint (includes `programmes` array + `alsoMemberOf` per child) | S | queued |
| `PROJ-MCP-1` | MCP tool `getSubCollections({collectionAppId})` | XS | queued |
| `PROJ-PANEL-1` | Frontend `CollectionSubCollectionsPanel.vue` (with programme strip + alsoMemberOf chips) | S | queued |
| `PROJ-NAV-1` | Frontend top-nav route `/projects` + `pages/projects/index.vue` (with programme side-filter) | S | queued |
| `PROJ-BADGE-1` | Project + "member of" chips on `CollectionList.vue` rows (multi-parent chip group) | XS | queued |
| `PROJ-ADMIN-RUNBOOK-1` | Three-audience docs: admin runbook for "mark as Project" + "add sub-Collection (non-exclusive)" + "set programme metadata" | XS | queued |
| `PROJ-SEMA-WRITE-GATE-1` | `SemanticAnnotationService` enforces SHACL constraints at write time | S | queued |

Total estimated effort: ~1 working day across backend + frontend.

## 8. MFFD application — what changes vs the original 119 spec

`aidocs/integrations/119-mffd-collection-layout.md` is revised in this PR:

- `mffd-project` Collection (renamed from `mffd-programme`) carries
  `urn:shepard:project = true` + `urn:shepard:programme = "Clean Aviation JU"`
  (and any additional DLR-internal programme line).
- The 6 step Collections each carry `urn:shepard:partOf = <mffd-project.appId>` —
  AND any other Project they also belong to (e.g. an
  `mffd-afp-tapelaying` Collection could also carry
  `urn:shepard:partOf = <clean-aviation-composites-project.appId>` for
  cross-cutting funder reporting; no extra Shepard write needed beyond the
  annotation).
- The Project's `project-overview` DO (formerly `programme-overview`)
  loses its custom "sub-collections" mention — the UI panel in §4.1 of this
  doc handles it generically.
- The operator gets the desired single entrypoint:
  1. `/projects` → "MFFD Upper Shell — Project" row → click.
  2. `mffd-project` Collection-detail page renders the 6 step tiles + the
     programme strip ("Clean Aviation JU").
  3. Click a tile → land on the step Collection.

The cross-Collection Predecessor edges from §3 of 119 still work the same
way — they walk `appId`, not Collection boundaries. The Project tree is
the *navigation* shape; Predecessor edges are the *lineage* shape; Programme
is *funder-line metadata*. All three coexist.

## 9. Generalisation — other projects

Once shipped, the same pattern serves:

| Project | Programme(s) | Children |
|---|---|---|
| `pluto-project` | DLR Project Line: Space Systems | PLUTO mission sub-collections (operations, payloads, telemetry, …) |
| `lumen-project` | DLR Project Line: Propulsion | Hot-fire test campaigns by quarter |
| `bt-kvs-project` | DLR Project Line: Composites | BT-KVS C/SiC fabrication campaigns |
| `home-showcase` | (none — personal demo) | Sensor groups in the home telemetry demo |

A Collection can be a member of more than one Project. For example, the
`lumen-tr004-anomaly-investigation` Collection can be
`partOf = lumen-project` AND `partOf = anomaly-research-project` —
non-exclusive bundling lets cross-cutting research initiatives gather their
relevant Collections without forcing a hierarchy.

No new code per Project — set the annotations, the UI handles the rest.

## 10. Open items

| # | Item | Plan |
|---|---|---|
| 1 | Should `urn:shepard:partOf` cascade ACL / delete? | **No.** Projects are loose registries; sub-Collections retain their own owners and lifecycles. Deleting a Project just leaves its children with one fewer `partOf` (their other Projects still own them); a Collection with zero `partOf` values is just a top-level Collection. |
| 2 | Programme as a controlled vocabulary, not free text? | Deferred. v1 is free-text strings so operators can write "Clean Aviation JU" without first registering it. When the same programme value appears on ≥3 Projects, surface a "promote to controlled term" prompt. The vocabulary registry already exists from SEMA-V6-PRED-UI. |
| 3 | Project hierarchy (Project of Projects)? | **Allowed via §2** — a Project Collection can itself carry `urn:shepard:partOf = <ancestor-project.appId>` (no constraint prevents this). The SHACL only requires that the target be a Project, which is satisfied. Whether the UI renders the recursive tree well is a separate ship gate; v1 renders one level only. |
| 4 | Sub-Collection ordering | Persist `urn:shepard:partOfOrdinal` on each child, scoped per-parent (since a child can belong to multiple Projects each with its own ordering). Defer until the first Project cares (MFFD process steps have a natural sequence — AFP → bridge → spot → NDT — so this lands together with MFFD). |

## 11. References

- `aidocs/integrations/119-mffd-collection-layout.md` — first application (MFFD)
- `aidocs/integrations/118-mffd-process-chain-mapping.md` — cross-Collection edges
- `aidocs/16` — backlog rows added (§7)
- `aidocs/strategy/102` — multi-project strategy
