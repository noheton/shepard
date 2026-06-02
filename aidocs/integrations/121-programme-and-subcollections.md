---
name: Programme entity + sub-Collection registry (urn:shepard:partOf)
description: Programme-as-registry pattern — an ordinary Collection with a urn:shepard:partOf annotation predicate that lets sibling Collections declare it as their parent, plus the Collection-detail panel + top-nav route + Collection-card badge that give the operator one entrypoint to a multi-step programme like MFFD.
type: design
stage: feature-defined
last-stage-change: 2026-06-02
---

# 121 — Programme entity + sub-Collection registry

**Status:** decision-quality 2026-06-02 (operator: "B sounds good but we need a programme UI").
**Companion:** `aidocs/integrations/119-mffd-collection-layout.md` (MFFD-specific application),
`aidocs/integrations/118-mffd-process-chain-mapping.md` (cross-Collection Predecessor edges).
**Replaces option D** (native `parentCollectionAppId` field) as the chosen
shape: this design adds no schema change, only an annotation predicate +
endpoint + UI panel.

## 0. TL;DR

A **Programme** is an ordinary `Collection` with a `urn:shepard:programme` role
annotation. Sibling Collections declare it as their parent with
`urn:shepard:partOf = <programme.appId>`. A new endpoint
`GET /v2/collections/{appId}/sub-collections` resolves the inverse. A new
panel on the Collection-detail page renders the children as tiles. A new
top-nav entry `/programmes` lists every Collection that has children — the
operator's **one entrypoint to multi-step initiatives** like MFFD, PLUTO,
LUMEN.

Zero schema change. Six small code changes (one Cypher migration to add the
`urn:shepard:partOf` + `urn:shepard:programme` predicates to the controlled
vocabulary; one backend endpoint; one frontend panel; one frontend top-nav
entry; one Collection-card badge; one SHACL shape).

## 1. Why this shape (vs the alternatives)

I considered five shapes (per the 2026-06-02 chat):

| | Option A: B-layout + custom `/mffd` route | **Option B: this (programme-as-registry)** | Option C: one big Collection | Option D: native `parentCollectionAppId` | Option E: TPL6 saved views |
|---|---|---|---|---|---|
| Data-model change | none | none (annotation only) | none | nullable column on `Collection` | none |
| Generalises beyond MFFD | no (`/mffd` is bespoke) | **yes** | no | yes | yes |
| One operator entrypoint | yes (custom route) | **yes** (programme tile + sub-collection panel) | yes (the Collection) | yes (parent in tree) | partly (saved view discoverability is poor) |
| Per-step ACL / DOI / SHACL | yes (6 Collections) | **yes** (6 Collections) | no (collapses) | yes | yes |
| Scale fixes still in play | yes | **yes** | no (regresses) | yes | yes |
| Engineering cost | 1d (UI only) | **1d** | 0 (no work) | 2–3d + Neo4j migration + v1-compat carve-out | 0.5d (label only) |
| Maintenance debt | medium (per-programme route) | **low** | high (scale defects) | medium (schema migration) | low |

Option B wins on the row that matters most: **same engineering cost as a
one-off custom route, but it generalises to every future programme** (PLUTO,
LUMEN, BT-KVS, …) without adding more code.

## 2. The annotation contract

Two new predicates added to the controlled vocabulary (V##__add_programme_predicates.cypher):

```
urn:shepard:programme               role marker on a Collection that says
                                    "this Collection is a Programme — it expects
                                    child Collections via urn:shepard:partOf"
                                    Value = literal "true".

urn:shepard:partOf                  on any Collection, says "this Collection is a
                                    child of <other Collection appId>".
                                    Value = a Collection appId (UUID v7).
```

**Cardinality rules (SHACL):**

- `urn:shepard:programme` is set-once per Collection (0..1).
- `urn:shepard:partOf` is set-once per Collection (0..1). A Collection can only
  belong to ONE programme directly. Multi-programme membership is reserved for
  a future `urn:shepard:partOfAdditional` predicate if the need arises (none
  yet).
- The target of `urn:shepard:partOf` must itself carry `urn:shepard:programme = true`
  (SHACL constraint, enforced at write time). Prevents accidentally pointing
  a child Collection at another child Collection.

**Owner-write only:** mutating either predicate requires the `instance-admin`
role OR ownership of *both* the parent Programme and the child Collection.
Enforced in `SemanticAnnotationService.write()`.

## 3. Backend — single new endpoint

### 3.1 `GET /v2/collections/{appId}/sub-collections`

```
Path  : /v2/collections/{appId}/sub-collections
Method: GET
Auth  : same as /v2/collections/{appId} (read)
Params:
  ?include=full           default: trimmed shape (id, appId, name, hero, doCount, lastActivity)
                          full: same shape as /v2/collections/{appId} response

Response 200:
  {
    "parentAppId": "<this collection appId>",
    "parentIsProgramme": true|false,
    "subCollections": [
      {
        "appId": "...", "id": ..., "name": "...", "heroImage": "...",
        "doCount": 8251, "lastActivity": "...", "ownerGroup": "..."
      },
      ...
    ]
  }
```

**Cypher (shape):**

```cypher
MATCH (c:Collection {appId: $parentAppId})
OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(prog:SemanticAnnotation {predicate: 'urn:shepard:programme'})
OPTIONAL MATCH (child:Collection)-[:HAS_SEMANTIC_ANNOTATION]->(p:SemanticAnnotation {predicate: 'urn:shepard:partOf', value: $parentAppId})
WHERE child <> c
RETURN c.appId         AS parentAppId,
       (prog IS NOT NULL) AS parentIsProgramme,
       collect(child) AS subCollections
```

`SubCollectionsRest` resource → `SubCollectionsService` → `SubCollectionsDAO`.
Returns 404 when the parent Collection doesn't exist. Returns 200 with empty
`subCollections: []` when the parent has no children (this is the common case
— most Collections are not programmes).

### 3.2 No new write endpoints

The `urn:shepard:programme` and `urn:shepard:partOf` annotations are set via
the existing `POST /v2/collections/{appId}/annotations` endpoint, gated by
the §2 SHACL constraints. No new write surface.

### 3.3 v1 compatibility

`/shepard/api/...` endpoints (upstream-frozen surface) are untouched.
Programme-aware behaviour is exposed only via `/v2/`. Per CLAUDE.md
§API-version-policy, this stays additive.

### 3.4 MCP coverage

One new MCP tool: `getSubCollections({collectionAppId})` → same response
shape as the REST endpoint. Lets Claude / agent clients walk the programme
tree without hand-rolling SemanticAnnotation queries.

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
└──────────────────────────────────┘
```

Click → navigate to the child Collection's detail page (same route shape).
The panel hides itself entirely when there are no children — so this is
zero UI cost for Collections that aren't programmes.

### 4.2 Top-nav route `/programmes` (`pages/programmes/index.vue`)

A new top-level nav entry between `/collections` and `/containers`. Lists
every Collection that carries `urn:shepard:programme = true`. Each row:

- name + hero image
- child-Collection count
- aggregate DO count across children
- last activity (max of children)
- owner group

Click → that Programme's Collection-detail page (which renders the
sub-collections panel from §4.1).

**Backend support:** `GET /v2/collections?filter=is-programme=true` — extends
the existing list endpoint with one filter predicate. Or, simpler: query the
existing list endpoint and let the frontend filter (the count of programmes
is small — single-digit per instance for years to come). I'd ship the
frontend filter and add the backend filter in a follow-up only if it shows
up in DB-OPT measurements.

### 4.3 Programme badge on Collection cards (`CollectionList.vue`)

When a Collection has `urn:shepard:programme = true`, render a small chip
("Programme · 6 sub-collections") on its row in the master Collections list.
Lets users discover the programme structure from the standard browse path,
not just `/programmes`.

When a Collection has `urn:shepard:partOf = <X>`, render a small ghost chip
("child of MFFD Programme") that links to the parent — so a user landing on
`mffd-afp-tapelaying` from a search result sees that it's part of something
larger and can navigate up.

## 5. SHACL shapes

Added to `backend/src/main/resources/shacl/programme.shape.ttl` (or whatever
the in-instance SHACL bundle is at ship time):

```turtle
shepard:ProgrammeShape
    a sh:NodeShape ;
    sh:targetClass shepard:Collection ;
    sh:property [
        sh:path shepard:programme ;
        sh:datatype xsd:boolean ;
        sh:minCount 0 ;
        sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shepard:partOf ;
        sh:datatype xsd:string ;
        sh:minCount 0 ;
        sh:maxCount 1 ;
        sh:sparql [
            sh:select """
                SELECT $this WHERE {
                    $this shepard:partOf ?parent .
                    FILTER NOT EXISTS { ?parent shepard:programme true }
                }
            """ ;
            sh:message "urn:shepard:partOf target must itself be a Programme" ;
        ]
    ] .
```

A Cypher migration (V##__seed_programme_predicates.cypher) adds both
predicates to the `urn:shepard:*` controlled-vocab registry so they appear
in the admin vocabulary picker.

## 6. CLAUDE.md compliance audit

- **§ship a UI stub for every backend feature**: ✅ §4.1 (panel) + §4.2 (route) + §4.3 (badge) — three UI surfaces.
- **§top-nav reachable before beta**: ✅ §4.2 — the `/programmes` route is in the top navbar.
- **§tools in-context first**: ✅ §4.1 is the primary entry (the Programme tile on its Collection-detail page); `/programmes` is the fallback global list.
- **§every reference type ships full CREDL**: not applicable — no new reference type; just two new predicates on existing entity.
- **§API-version policy**: ✅ §3 — new endpoint lives at `/v2/`.
- **§singleton FileReference**: not applicable.
- **§UI never asks for paths/URLs**: ✅ — parent Collection is picked by appId via a Collection picker, not by typing a URL.
- **§schema changes additive + nullable**: ✅ — no schema change at all.
- **§secondary writes fire-and-forget**: ✅ — annotations write via existing service.
- **§three-audience docs**: covered by this design doc + the admin runbook
  (TBD: how an admin marks a Collection as a Programme + adds child Collections).

## 7. Backlog rows added by this doc

To `aidocs/16-dispatcher-backlog.md`:

| ID | Subject | Size | Status |
|---|---|---|---|
| `PROG-PREDICATES-1` | Cypher migration: add `urn:shepard:programme` + `urn:shepard:partOf` to controlled vocab + SHACL shape | S | queued |
| `PROG-REST-1` | Backend `GET /v2/collections/{appId}/sub-collections` endpoint | S | queued |
| `PROG-MCP-1` | MCP tool `getSubCollections({collectionAppId})` | XS | queued |
| `PROG-PANEL-1` | Frontend `CollectionSubCollectionsPanel.vue` on `pages/collections/[collectionId]/index.vue` | S | queued |
| `PROG-NAV-1` | Frontend top-nav route `/programmes` + `pages/programmes/index.vue` | S | queued |
| `PROG-BADGE-1` | Programme + "child of" chips on `CollectionList.vue` rows | XS | queued |
| `PROG-ADMIN-RUNBOOK-1` | Three-audience docs: admin runbook for "mark a Collection as a Programme" + "add a sub-Collection" | XS | queued |
| `PROG-SEMA-WRITE-GATE-1` | `SemanticAnnotationService` enforces SHACL programme/partOf constraints at write time | S | queued |

Total estimated effort: ~1 working day across backend + frontend (small
incremental rows, each individually mergeable).

## 8. MFFD application — what changes vs the original 119 spec

`aidocs/integrations/119-mffd-collection-layout.md` is revised in this PR:

- `mffd-programme` Collection now carries `urn:shepard:programme = true`.
- The 6 step Collections each carry `urn:shepard:partOf = <mffd-programme.appId>`.
- The Programme's `programme-overview` DO (§2.1 in 119) loses its custom
  "sub-collections" mention — the UI panel in §4.1 of this doc handles it
  generically.
- The operator gets the desired single entrypoint:
  1. `/programmes` → "MFFD Upper Shell — Programme" row → click.
  2. `mffd-programme` Collection-detail page renders the 6 step tiles.
  3. Click a tile → land on the step Collection.

The cross-Collection Predecessor edges from §3 of 119 still work the same
way — they walk `appId`, not Collection boundaries. The Programme tree is
the *navigation* shape; Predecessor edges are the *lineage* shape. Both
coexist.

## 9. Generalisation — other programmes

Once shipped, the same pattern serves:

| Programme | Children | Notes |
|---|---|---|
| `pluto-programme` | PLUTO mission sub-collections (operations, payloads, telemetry, …) | see `aidocs/strategy/102` |
| `lumen-programme` | hot-fire test campaigns by quarter | `examples/lumen-showcase/seed.py` is already the canonical demo |
| `bt-kvs-programme` | BT-KVS C/SiC fabrication campaigns | `aidocs/btkvs/...` |
| `home-showcase` | sensor groups in the home telemetry demo | small but generalising; gets the UI affordance for free |

No new code per programme — set the two annotations, the UI handles the
rest. This is the long-term return on the 1-day investment.

## 10. Open items

| # | Item | Plan |
|---|---|---|
| 1 | Should `urn:shepard:partOf` cascade ACL / delete? | **No.** Programmes are loose registries; sub-Collections retain their own owners and lifecycles. Deleting a Programme just leaves its children orphaned (their `partOf` annotation becomes a dangling pointer; the UI hides the chip; existing TPL11 dangling-edge sweep marks them). |
| 2 | Multi-Programme membership? | Deferred to `urn:shepard:partOfAdditional` when the first concrete case appears. PLUTO + a sister mission sharing a payload is the obvious candidate. |
| 3 | Programme hierarchy (Programme of Programmes)? | Not in v1. If we hit it (DLR programme line = parent of MFFD), add `urn:shepard:programmeParent` as a separate predicate so it doesn't pollute the simple Programme→Collection edge. |
| 4 | Sub-Collection ordering | Persist `urn:shepard:partOfOrdinal = <integer>` on each child for stable tile order. Defer until the first programme cares (MFFD process steps have a natural sequence — AFP → bridge → spot → NDT — so this lands together with MFFD). |

## 11. References

- `aidocs/integrations/119-mffd-collection-layout.md` — first application (MFFD)
- `aidocs/integrations/118-mffd-process-chain-mapping.md` — cross-Collection edges
- `aidocs/16` — backlog rows added (§7)
- `aidocs/strategy/102` — multi-programme strategy
