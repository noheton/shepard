# Templates as a First-Class Entity — Design

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. Replaces the existing `__templates` hack
and lights up the admin-templates page.

**Scope.** Promote templates from "a `DataObject` inside a
reserved-name `Collection` called `__templates`" (the shape locked
in `aidocs/39`) to a **first-class Neo4j entity** (`:ShepardTemplate`)
living in an admin-only subgraph, with its own permission model, its
own REST surface under `/v2/templates/...`, and a dedicated admin
page. Reconciles the user observation:

> "Templates `__templates` really? ... maybe an admin only subgraph
> neo4j entity? and a separate type of entity 'ShepardTemplate' /
> would provide admins maybe with an admin page in the long run."

Bakes in the previously-locked user constraint that **Collection
owners decide which templates from the global repository are
allowed inside their Collection** (`aidocs/39 §3`, re-expressed as
a graph edge here).

---

## 1. The problem with `__templates` today

**`__templates` is not yet in the code.** A grep across
`backend/src/` returns zero hits; mentions live only in `aidocs/16`
(T1a), `aidocs/39` (the design), `aidocs/40 §76`, and `aidocs/50
§EXP1k`. The hack is **designed but not shipped** — we still have a
clean shot at replacing it before any operator's database carries
the sentinel.

The shape `aidocs/39` proposes (and this doc retires):

- A `Collection` named **`__templates`** (double-underscore prefix
  is a Python-flavoured "private, do not touch" sentinel borrowed
  from a language shepard doesn't use server-side).
- `PUBLIC` reads; admin-only writes enforced by an out-of-band role
  check in template endpoints — **not** the standard permission
  graph.
- Templates are `DataObject`s discriminated by a `templateKind`
  attribute string.
- Per-Collection allow-list is a `Set<String> allowedTemplateAppIds`
  field on every `Collection`.

Five problems, worst-first:

1. **Sentinel naming is brittle.** The reserved Collection name
   `__templates` collides with any user who picks the same name.
   `aidocs/39 §9` admits this and proposes "refuse to start with a
   clear error" — operator-hostile for a name shepard chose.
2. **Authorisation is bolted on.** Two sources of truth ("real"
   permission graph vs. parallel role check in template
   endpoints) is the anti-pattern `aidocs/24` flags as fragility.
3. **Hard to query separately.** "Show me every template" becomes a
   three-hop Cypher with a string-equality predicate on a
   user-namespaced field. `MATCH (t:ShepardTemplate) RETURN t` is
   one hop and uses the label index.
4. **Templates mix with user data in every projection.** Search,
   exports, semantic-annotation walks, RO-Crate crates — every one
   has to know to *skip* the `__templates` Collection, or templates
   leak into user-facing answers. Every new surface is a place this
   leak can regress.
5. **No first-class admin UI.** Reusing the regular DataObject
   editor to edit typed-blueprint metadata (`AttributeSpec`,
   `FileSlot`) is user-hostile and special-cases the editor.

Templates haven't shipped yet. The cheapest moment to replace the
shape is **before T1a lands**.

---

## 2. The proposed shape

### 2.1 The entity

A new Neo4j node label `:ShepardTemplate`, with `HasAppId` per the
L2 chain (`aidocs/25`):

```java
@NodeEntity("ShepardTemplate")
public class ShepardTemplate implements HasAppId {
  @Id @GeneratedValue private Long id;
  @Property private String appId;                       // UUID v7 (L2a)
  @Property private String name;
  @Property private TemplateKind templateKind;          // enum (§2.2)
  @Property private int version;                        // copy-on-write per §2.3
  @Property private String body;                        // JSON DSL — §7
  @Property private String description;
  @Property private List<String> tags;
  @Property private String iconRef;                     // optional FileReference appId
  @Property private String createdBy;
  @Property private Instant createdAt;
  @Property private Instant updatedAt;
  @Property private boolean retired;                    // soft-delete flag
  @Property private String legacyBackfill;              // null except V15 (§8)
}
```

Mirrors `Role` (shipped in A0 / `aidocs/51`) so L2a `HasAppId`
plumbing carries over unchanged.

### 2.2 `TemplateKind` enum

```java
public enum TemplateKind {
  DATAOBJECT_RECIPE,    // blueprint for one DataObject (the #630 shape)
  COLLECTION_RECIPE,    // blueprint for a whole Collection subtree (T1h)
  EXPERIMENT_RECIPE,    // recipe for the experiment-coordinator (aidocs/50)
  PROCESS_RECIPE        // (post-PR1) typed sequence of templated steps
}
```

Open to extension. Discriminator lives in the entity, not the body
— querying by kind stays a single-property predicate.

### 2.3 Versioning — copy-on-write

| Shape | Verdict |
|---|---|
| In-place edit + `version` counter bumped | Rejected. Old version is no longer fetchable; reproducing how a past Collection was created becomes impossible. Breaks `aidocs/41` snapshot reproducibility. |
| **Copy-on-write — each edit mints a new `:ShepardTemplate` node** with `version` incremented; prior node marked `retired = true` for picker filtering but kept on disk | **Recommended.** Past Collections keep a stable reference. |

```cypher
(:ShepardTemplate {appId: $newId, version: 4})
  -[:SUPERSEDES]->
(:ShepardTemplate {appId: $oldId, version: 3})
```

`USES_TEMPLATE` (§3) points at the specific version-node. Walking
`:SUPERSEDES` chains backwards gives the admin a "show every
version" view.

### 2.4 Admin-only subgraph

`:ShepardTemplate` nodes carry no `:has_permissions` edge, no
Owner/Reader/Writer/Manager relation. The label itself is the
authorisation key — only place "can this user touch templates" is
answered is the `instance-admin` role check (§4) on REST.

```cypher
CREATE CONSTRAINT template_appId_unique IF NOT EXISTS
FOR (t:ShepardTemplate) REQUIRE t.appId IS UNIQUE;
```

`PermissionsService.filterAllowedForUser` (post-P2) gains a
short-circuit: `:ShepardTemplate` labels skip the user-permission
walk. Templates never appear in search projections, export crates,
or annotation walks unless the caller explicitly hits
`/v2/templates/...`.

---

## 3. Relationships and visibility

### 3.1 `:USES_TEMPLATE` — instantiation provenance

```cypher
(:Collection {appId: $collId})
  -[:USES_TEMPLATE {at: $ts, version: $v}]->
(:ShepardTemplate {appId: $tplId, version: $v})
```

Also at DataObject level:

```cypher
(:DataObject {appId: $doId})
  -[:CREATED_FROM_TEMPLATE {at: $ts, version: $v}]->
(:ShepardTemplate {appId: $tplId})
```

Replaces `aidocs/39 §2.3`'s edge-plus-sneaker-attribute pair. The
sneaker attribute goes away; the edge alone is canonical.

### 3.2 `:ALLOWS_TEMPLATE` — per-Collection curator pinning

```cypher
(:Collection {appId: $collId})
  -[:ALLOWS_TEMPLATE {pinnedBy: $username, pinnedAt: $ts}]->
(:ShepardTemplate {appId: $tplId})
```

The user-required constraint, now a first-class edge instead of a
`Set<String>` field on `Collection`. Anyone with `MANAGE` on the
parent Collection pins which templates are usable inside it. Zero
edges = **unrestricted** (picker shows all non-retired templates
matching the target kind). One or more edges = **restricted**.

Trade-off vs. `aidocs/39 §3.1`'s field shape: the edge form costs
one extra hop per picker fetch but gets referential integrity for
free (delete a template → its edges disappear). Picker fetches are
rare; the cost is a rounding error.

### 3.3 `:CAN_AUTHOR` — reserved for delegation

Not in v1. **Reserved syntactic slot** for future delegation
("Alice can author templates, but isn't full `instance-admin`").
Until demand arrives, all template mutations gate on
`instance-admin` (§4); `:CAN_AUTHOR` stays unwired.

---

## 4. Authorisation

Single source of truth: the `instance-admin` role from `aidocs/51`.

| Action | Gate |
|---|---|
| Mint (POST), edit (PUT), delete/retire (DELETE) | `@RolesAllowed("instance-admin")` |
| Read (GET) | Authenticated user |
| Cite (POST `/from-template/...`) | Read on parent Collection AND (template in effective allow-list OR caller is `instance-admin`) |
| Edit `:ALLOWS_TEMPLATE` edges | `MANAGE` on the Collection (existing Permissions graph) |

Admin override preserved: `instance-admin` can instantiate any
template into any Collection, bypassing the per-Collection
allow-list. Makes the admin page's "Try this template here"
preview work without first editing allow-lists.

**Soft-delete semantics.** `DELETE /v2/templates/{appId}` flips
`retired = true` if any `:USES_TEMPLATE` / `:CREATED_FROM_TEMPLATE`
back-references exist. Hard-delete returns `409 Conflict` with the
back-reference count if any remain. Same shape as snapshot
retirement in `aidocs/41`.

---

## 5. REST surface (`/v2/templates/...`)

Per `CLAUDE.md`'s API-version policy, every new endpoint lands
under `/v2/`. Upstream `/shepard/api/...` paths get zero new
routes.

| Method + path | Purpose | Gate |
|---|---|---|
| `GET /v2/templates` | List, filterable by `?kind=&tag=&retired=` | Authenticated |
| `GET /v2/templates/{appId}` | Fetch one (body + metadata) | Authenticated |
| `POST /v2/templates` | Mint | `instance-admin` |
| `PUT /v2/templates/{appId}` | Edit — copy-on-write, returns the new version | `instance-admin` |
| `DELETE /v2/templates/{appId}` | Soft-retire or hard-delete if no back-refs | `instance-admin` |
| `GET /v2/templates/{appId}/usage` | Backref view — which Collections cite this template | `instance-admin` |
| `GET /v2/collections/{appId}/allowed-templates` | Effective allow-list (picker feed) | Read on Collection |
| `POST /v2/collections/{appId}/allowed-templates` | Add `:ALLOWS_TEMPLATE` edge | `MANAGE` on Collection |
| `DELETE /v2/collections/{appId}/allowed-templates/{templateAppId}` | Remove `:ALLOWS_TEMPLATE` edge | `MANAGE` on Collection |
| `POST /v2/collections/{appId}/from-template/{templateAppId}` | Instantiate | Read + allow-list (or `instance-admin`) |
| `POST /v2/templates/import` | Bulk import — zipped JSON pack | `instance-admin` |
| `GET /v2/templates/export` | Bulk export — zipped JSON pack | `instance-admin` |

`GET /v2/collections/{appId}/allowed-templates` keeps the same wire
shape as `aidocs/39 §3.2`; only the internals shift (from
`Set<String>` membership to `:ALLOWS_TEMPLATE` edge walk + zero-edge
fallback to "all non-retired"). Callers don't notice. Admin
endpoints reuse the JAX-RS `SecurityContext` plumbing shipped in A0
per `aidocs/51 §7`.

---

## 6. The admin page

A new pane in the admin section (per `aidocs/22 §4.x` admin-CLI +
the future `/admin` UI route from `aidocs/36`). Gated on
`JWTPrincipal.roles.contains("instance-admin")`.

| Tab | What |
|---|---|
| **Templates** | Tabular list (filterable by kind, tag, retired). Inline create/edit/retire. |
| **Usage** | For a selected template: every Collection citing it + "Try it here" admin-override quick-action. |
| **Versions** | Walks the `:SUPERSEDES` chain. Textual JSON diff for v1; side-by-side diff later. |
| **Import / Export** | Drop-zone for zipped template pack + "Export all" button. |

For `DATAOBJECT_RECIPE` / `COLLECTION_RECIPE`: Monaco-flavoured
JSON editor with schema-aware autocomplete against the JSON DSL
(§7) JSON Schema. For `EXPERIMENT_RECIPE`: same editor with the
coordinator-service schema (per `aidocs/50`). For `PROCESS_RECIPE`:
deferred to PR1.

**CLI alternative** (per §10.5): a `shepard-admin template`
sub-command shipping the same operations:

```
shepard-admin template list
shepard-admin template show <appId>
shepard-admin template create --file recipe.json
shepard-admin template edit <appId> --file recipe.json
shepard-admin template retire <appId>
shepard-admin template export --out templates.zip
shepard-admin template import templates.zip
```

CLI = operator-friendly path. Admin page = discovery-friendly path.
Both hit `/v2/templates/...`.

---

## 7. Template body — what's the recipe format

Three candidates:

| # | Shape | Pros | Cons |
|---|---|---|---|
| (a) | **JSON DSL** — `{ collection: {…}, dataobjects: […], references: […] }` | Client-side validatable; schema-aware editors free; serialises to RO-Crate; reuses `AttributeSpec` / `FileSlot` from `aidocs/39 §2.4-2.5`. | Less human-authorable than Markdown; admins in cypher-shell see a JSON blob. |
| (b) | **Markdown + frontmatter** | Casual-authoring friendly; aligns with J1 lab-journal Markdown choice (`aidocs/37`). | Round-trip is lossy; Markdown's casual structure makes deterministic re-projection back to graph fragments fiddly. |
| (c) | **Cypher** — server runs at instantiation | Maximally powerful; matches migration pattern. | Arbitrary-execution surface — Cypher injection (C5) lives here; no client validation; admins eyeballing in `cypher-shell` see executable code. |

**Recommendation: (a) JSON DSL for v1.** Reasoning:

- The body is the **storage format**; the editor surface is
  independent. A future Markdown wrapper can serialise to JSON DSL
  on save without re-architecting storage.
- JSON DSL composes with L2 `appId` discipline — every referenced
  entity carries a placeholder `appId` the instantiation resolves
  against the target Collection.
- JSON DSL is **inert at rest**. (c) would expand C5's
  Cypher-injection blast radius to every template the admin mints.
- Monaco's schema-aware autocomplete rides for free off the JSON
  Schema document we publish alongside.

Sketch:

```json
{
  "templateKind": "DATAOBJECT_RECIPE",
  "name": "LUMEN Hot-Fire Run",
  "attributes": [
    { "name": "campaign",      "type": "ENUM",   "required": true,  "allowed": ["Q3-2024","Q4-2024"] },
    { "name": "test_engineer", "type": "STRING", "required": true,  "hint": "Person who ran the test" },
    { "name": "is_fired",      "type": "BOOL",   "required": true }
  ],
  "fileSlots": [
    { "name": "test_report", "allowedMimeTypes": ["text/markdown", "application/pdf"], "required": true }
  ],
  "references": []
}
```

Reuses `AttributeSpec` / `FileSlot` from `aidocs/39 §2.4-2.5`
verbatim — same JSON shape, now on `:ShepardTemplate.body` instead
of a Mongo blob on a sentinel DataObject.

Path to (b): a `templates/{appId}/render` endpoint emits a Markdown
view; editor saves serialise back to JSON DSL. Out of scope for v1;
slice T1g if demand materialises.

---

## 8. Migration from `__templates`

### 8.1 Greenfield (most operators)

No `__templates` Collection exists (upstream baseline has no
templates feature). V15 creates the `:ShepardTemplate` constraint
and exits cleanly. Cost: one `CREATE CONSTRAINT IF NOT EXISTS`.

### 8.2 Operators who shipped T1a-from-aidocs/39 first

Hypothetical edge case for any deployment that ran T1a from
`aidocs/39` before this lands (probably zero). The V15 migration
walks the legacy `__templates` Collection's DataObjects, reifies
each as a `:ShepardTemplate`, and copies typed-blueprint fields
from the legacy Mongo blob:

```cypher
// V15__Migrate_legacy_templates.cypher
MATCH (c:Collection {name: '__templates'})-[:HAS_DATA_OBJECT]->(d:DataObject)
WHERE d.templateKind IS NOT NULL
WITH d, randomUUID() AS newAppId
MERGE (t:ShepardTemplate {appId: newAppId})
  SET t.name = d.name,
      t.templateKind = d.templateKind,
      t.version = 1,
      t.body = d.body,
      t.description = d.description,
      t.tags = coalesce(d.tags, []),
      t.createdBy = d.createdBy,
      t.createdAt = d.createdAt,
      t.updatedAt = d.updatedAt,
      t.retired = false,
      t.legacyBackfill = 'T1a-V15';
```

Idempotent (`MERGE` no-op on existing). Fail-fast on parse errors
per the `MigrationsRunner` post-A1e contract. The `legacyBackfill`
tag marks every migrated node for rollback.

Rollback (`V15_R__Rollback_templates.cypher`):

```cypher
MATCH (t:ShepardTemplate {legacyBackfill: 'T1a-V15'}) DETACH DELETE t;
DROP CONSTRAINT template_appId_unique IF EXISTS;
```

Operator runs via `cypher-shell` per the `CLAUDE.md` comfort rule.

### 8.3 Tracker rows

- `aidocs/34`: **AWARE** greenfield (no operator action);
  **BREAKING** flagged only if any T1a-shipped instance with the
  legacy `__templates` Collection still exists when this lands.
- `aidocs/44`: replaces "T1: Templates (designed)" with
  "T1: Templates (designed; first-class entity per `aidocs/54`)."

---

## 9. Phasing

Each slice ≤ 1-2 weeks.

| ID | Slice | Size | Gate | Decision? |
|---|---|---|---|---|
| **T1a** | `:ShepardTemplate` entity + V15 migration + V15_R rollback + `GET /v2/templates` returning empty list + `GET /v2/templates/{appId}`. Read-only, no UI. | S | L2c | No |
| **T1b** | `POST` / `PUT` / `DELETE /v2/templates`, copy-on-write versioning, JSON DSL schema + JSON Schema doc. All mutating endpoints `instance-admin`-gated. | M | T1a | **Yes** — §10.1 JSON DSL vs Markdown |
| **T1c-cli** | `shepard-admin template {list,show,create,edit,retire,export,import}` CLI. | S | T1b | **Yes** — §10.5 CLI-first vs admin-page-first |
| **T1c** | Admin page — Templates / Usage / Versions / Import-Export tabs. Monaco JSON editor with schema autocomplete. | M | T1b | Yes (paired with T1c-cli) |
| **T1d** | `:ALLOWS_TEMPLATE` edges + per-Collection allow-list endpoints + picker feed. | S | T1b | No |
| **T1e** | `POST /v2/collections/{appId}/from-template/{templateAppId}` instantiation flow. `:CREATED_FROM_TEMPLATE` + `:USES_TEMPLATE` edges. Frontend picker. | M | T1b + T1d | No |
| **T1f** | Bulk import/export — zipped JSON template packs. | M | T1b | No |
| **T1g** | (deferred) Markdown-with-frontmatter user-facing wrapper. | M | T1b | Demand-gated |
| **T1h** | (deferred) "Update instance to latest template version" — opt-in `:CREATED_FROM_TEMPLATE` re-binding along `:SUPERSEDES`. | M | T1b + T1e | No |

Recommended order: **T1a → T1b → T1d → T1c-cli → T1c → T1e → T1f.**
T1c-cli ships before T1c so operators get a usable surface first;
admin page is the polish slice.

---

## 10. Open questions for the maintainer

Each row has a recommended default.

1. **JSON DSL vs Markdown body (§7).** Recommend (a) **JSON DSL**
   for v1; Markdown wrapper deferred to T1g. **Owner:** maintainer
   signoff before T1b dispatch.
2. **Default-template-bundle in the showcase seed.** Should
   `examples/seed-showcase/seed.py` mint LUMEN-style starter
   templates (`hot-fire-run-recipe`, `calibration-run-recipe`,
   `post-run-debrief-recipe`)? Recommend **yes** — same casual-user
   payoff as the showcase Collection.
3. **`EXPERIMENT_RECIPE` storage location** (per `aidocs/50`). In
   `:ShepardTemplate` (this design's answer) or in the
   experiment-coordinator service's own data store? Recommend
   **`:ShepardTemplate`** — single source of truth, single admin
   page, picker UI reuses. Coordinator fetches via `GET /v2/templates/{appId}`.
4. **Versioning shape (§2.3).** Copy-on-write (recommended) vs
   in-place edit + `version` field. Recommend **copy-on-write** —
   buys reproducibility for back-references. Cost: more nodes over
   time; cheap.
5. **Admin page first (T1c) vs CLI first (T1c-cli).** Recommend
   **CLI first** — operators get a usable surface immediately;
   admin page is polish. Reverses the order implied by the brief.
6. **`:CAN_AUTHOR` delegation in v1?** Recommend **no** — defer
   until demand. The edge type is reserved (syntactic slot only),
   wireable without a migration.
7. **Picker UX on a zero-`:ALLOWS_TEMPLATE` Collection.** Show "all
   templates" or "no templates" by default? Recommend **all
   templates** (the `aidocs/39 §3.2` shape) — zero edges = no
   restriction matches existing semantics; UI copy disambiguates
   "Restricted to N templates" / "Unrestricted (all global
   templates allowed)."
8. **URL namespace.** `/v2/templates/...` (recommended) vs
   `/v2/admin/templates/...` (parallels `aidocs/51`'s `/v2/admin/...`).
   Recommend **`/v2/templates/...`** because non-admin users *can*
   read templates (the picker is a user surface); URL shouldn't
   mislead about authz.

---

## 11. Cross-references

- **`aidocs/16`** — T1 series rows: T1a-T1h numbering retained; slice
  contents shift to match §9. T1a's "`__templates` auto-created at
  start" line gets rewritten to "`:ShepardTemplate` entity + V15."
- **`aidocs/22`** — gains `shepard-admin template ...` sub-commands
  (§6 CLI). Slice T1c-cli.
- **`aidocs/25`** (L2 chain) — `:ShepardTemplate` is `HasAppId` from
  day 1; V15 generates UUIDs app-side. T1a gates on L2c.
- **`aidocs/34`** — new AWARE row for V15; BREAKING flag only if any
  T1a-from-aidocs/39 instance exists when this lands.
- **`aidocs/39`** — predecessor design. Superseded for the
  `__templates` Collection choice. `AttributeSpec` / `FileSlot` /
  per-Collection allow-list user-constraint decisions stay; storage
  shape changes to `:ShepardTemplate` + edges.
- **`aidocs/40 §2`** — `PROCESS_RECIPE` kind reserved for PR1.
- **`aidocs/42`** — vision rows for Templates (T1) stay; the linked
  design swaps from `aidocs/39` to `aidocs/54` once this lands.
- **`aidocs/44`** — T1 row notes flip to cite this doc.
- **`aidocs/47`** (plugin SPI) — `:ShepardTemplate` is a **core
  primitive**, not a plugin. The PayloadKind / PayloadStorage SPI
  ships payload kinds; templates are recipes that *configure*
  payload-kind instantiation. No plugin coupling.
- **`aidocs/50`** — `EXPERIMENT_RECIPE` location resolved per §10.3.
- **`aidocs/51`** — every mutating endpoint in §5 carries
  `@RolesAllowed("instance-admin")`; role plumbing ships in A0
  ahead of this slice.
- **`aidocs/49`** — `docs/reference/templates.md` +
  `docs/help/use-a-template.md` ship in the same PR as T1e per
  `CLAUDE.md` docs-currency rule.

---

## 12. What this isn't

- **Not a workflow engine.** Templates declare *what* gets created,
  not *how* it transitions. Step sequencing is PR1's job
  (`aidocs/40 §2`); experiment driving is `aidocs/50`'s.
- **Not a Turing-complete template DSL in v1.** JSON DSL is
  declarative — no loops, no conditionals, no computed fields.
  Conditional templates ("if `is_fired = true`, require
  `test_report`") are a v2 enhancement of `AttributeSpec` (per
  `aidocs/39 §9`).
- **Not auto-applying templates on existing Collections.** A
  Collection minted before a template existed is not retroactively
  bound, even if its structure matches. Binding requires explicit
  instantiation.
- **Not a multi-tenant template marketplace.** Templates are
  instance-scoped; cross-instance distribution is export/import
  (T1f), not federation.
- **Not a permission-graph contributor.** `:ShepardTemplate` carries
  no permission edges; `instance-admin` is the single authz surface.
  By design — templates are configuration, not user content.
- **Not a substitute for code review on the body.** A maliciously
  authored template's JSON DSL is inert (no execution semantics),
  but a misconfigured `AttributeSpec.allowed` or surprising
  `FileSlot.allowedMimeTypes` ships to every user the
  Collection-allow-list exposes. Admins review templates before
  publishing — T1f import shows a diff preview for this.
