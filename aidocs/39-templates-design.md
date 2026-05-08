# Templates — Implementation Design

**Scope.** Implementation design for the Templates feature. Reconciles
the L3 backlog row ("YAML-defined templates") with issue **#630**
("Templates Collection of DataObject blueprints" + Figma mock-up)
and bakes in the user's constraint that **Collection owners decide
which templates from the global repository are allowed in their
Collection**.

**Status.** Design. No code or migration shipped.
**Snapshot date.** 2026-05-08.
**Originating items.** Issue **#630** (Data object templates,
Figma), backlog row **L3** (`aidocs/16`), user clarification:
"Collection owners should be able to define allowed templates from
a template repository."

## 1. The two-shapes question, settled

Two shapes were on the table:

- **L3:** *"YAML-defined templates for collections / data objects /
  refs (admin role)."* Portable across instances, gitops-friendly,
  no in-app authoring UI.
- **#630 + Figma:** *"A special Templates Collection ... DataObjects
  in the template collection can be used as blueprint."* Native
  shepard primitives, in-app authoring, no YAML parser.

**Recommendation: ship the #630 shape as the primary model**, with
**YAML import/export as an optional admin tool** for portability.
This satisfies both:

- Authors get a familiar in-app workflow (create a DataObject in the
  Templates Collection — same UX as creating any DataObject).
- Operators get the portability story (export the Templates
  Collection as YAML, commit to git, import on another instance).

YAML stays the **interchange format**, not the storage format.

## 2. The model

### 2.1 Templates Collection

- A **single, well-known Collection** named `__templates` (or
  similar reserved-name prefix).
- Created at first-server-start by a small data-init step (idempotent
  — present-and-named-correctly is the success condition).
- **Visibility: PUBLIC**. Every user can browse templates.
- **Edit permissions: admin only.** Enforced by a role check in the
  Templates-related endpoints, not by relying on the standard
  permission graph (defence in depth).

### 2.2 Template = DataObject + marker

A template is a `DataObject` inside `__templates` plus a marker
attribute:

```
DataObject "Sensor Run Template"
├─ attribute "templateKind"      = "DATA_OBJECT_BLUEPRINT"
├─ attribute "test_engineer"     = "" (required, str, hint="Person who ran the test")
├─ attribute "campaign"          = "" (required, str, allowed=["Q3-2024","Q4-2024",...])
├─ attribute "is_fired"          = false (required, bool)
└─ FileSlot "test_report"        = required: true, mime: text/markdown
```

- Re-uses the existing `Attribute` IO; gains a sibling
  `AttributeSpec` for required / type-hint / allowed-values metadata
  (see §2.4).
- Re-uses the existing `FileSlot` concept (proposed below as a new
  small node) — defines what file kinds a template requires/permits
  on instantiation.

### 2.3 The "created from template" relationship

Per #630 acceptance criterion 4: instances carry a relationship back
to their template.

```cypher
(:DataObject {appId: $instance})
  -[:CREATED_FROM_TEMPLATE {templateAppId: $tplId, templateVersion: 3, createdAt: ...}]->
  (:DataObject {appId: $tplId})
```

`templateVersion` pins to the version of the template at creation
time (templates can change after instances exist; pinning is the
honest answer per `aidocs/36 §10` template-frozen-or-live decision).
**Default: instances are pinned.** A future "update to latest
template" action is opt-in and recorded in the relationship's
revision history.

The relationship is **graph-edge plus a sneaker attribute**
(`templateAppId` on the instance) for query simplicity — Cypher
query-by-edge is fast; the attribute is the read-cache.

### 2.4 `AttributeSpec` — the small new IO

```java
public record AttributeSpec(
  String name,
  AttributeType type,         // STRING, INT, FLOAT, BOOL, ENUM, DATE, REF
  boolean required,
  Object defaultValue,         // nullable
  List<Object> allowedValues,  // nullable; non-null implies ENUM
  String description           // help text shown in the create-from-template form
) {}
```

Stored as a JSON blob on the template `DataObject` (Mongo, alongside
the StructuredData payload) rather than a per-attribute Neo4j node —
templates have small attribute counts (< 30 typical) so JSON-blob
storage avoids a graph explosion and keeps the read path fast.

### 2.5 `FileSlot` — required / permitted file payloads

```java
public record FileSlot(
  String name,                      // e.g. "test_report"
  Set<String> allowedMimeTypes,     // e.g. ["text/markdown", "application/pdf"]
  boolean required,
  String description
) {}
```

Same Mongo-blob storage. On create-from-template, the UI presents
upload prompts per slot; required slots block submission until
filled.

## 3. The per-Collection allow-list (user-required constraint)

Collection owners decide which templates from `__templates` can be
used in *their* Collection. Two design choices:

### 3.1 Storage shape

```java
@NodeEntity
public class Collection extends AbstractEntity {
  // ... existing fields ...
  @Property("allowedTemplateAppIds")
  private Set<String> allowedTemplateAppIds;  // empty = "all global templates allowed"
}
```

Empty set ↔ "no restriction; any template in `__templates` is
usable here." Non-empty set ↔ "only these templates are allowed."

This mirrors how `Permissions` works — a small per-Collection
configuration field that the Collection owner controls. **Owner
auth check** for editing it: must have MANAGE permission on the
Collection (consistent with #483 user-groups admin pattern).

### 3.2 Discovery shape

The frontend's "Create DataObject from template" picker:

```
GET /v2/collections/{appId}/allowed-templates
```

Returns the effective allow-list:
- If `allowedTemplateAppIds` is empty: all DataObjects in
  `__templates` with `templateKind = DATA_OBJECT_BLUEPRINT`.
- Otherwise: only those whose `appId` is in the set, intersected
  with the actual current contents of `__templates` (templates can
  be deleted; the allow-list silently drops stale entries on read).

The picker UI shows the user the effective list — they don't need
to know whether the Collection is restricted or not.

### 3.3 Owner-edit endpoint

```
PUT /v2/collections/{appId}/allowed-templates
{ "allowedTemplateAppIds": ["...", "..."] }   // empty array = unrestricted
```

Merge-patch (P21x) is overkill for a single-field array; PUT is
fine.

### 3.4 Admin override

Admin role can use any template regardless of the per-Collection
allow-list. Consistent with the admin-can-edit-`__templates` rule.

## 4. Endpoints (all under `/v2/`)

| Method + path | Purpose |
|---|---|
| `GET /v2/templates` | List all templates in `__templates` (admin browse + global picker) |
| `GET /v2/templates/{appId}` | Read one template (the DataObject + AttributeSpec + FileSlot) |
| `POST /v2/templates` | **Admin only.** Create a template (creates a DataObject in `__templates`) |
| `PATCH /v2/templates/{appId}` | **Admin only.** Edit (merge-patch) |
| `DELETE /v2/templates/{appId}` | **Admin only.** Soft-delete; instances retain their pinned version |
| `GET /v2/collections/{appId}/allowed-templates` | Effective allow-list for a Collection |
| `PUT /v2/collections/{appId}/allowed-templates` | **Owner.** Set / clear the allow-list |
| `POST /v2/collections/{appId}/data-objects/from-template/{templateAppId}` | Instantiate a template into a Collection. Body fills in the AttributeSpec values + FileSlot uploads. Validates required-fields. Creates the `[:CREATED_FROM_TEMPLATE]` edge. |
| `POST /v2/templates/import` | **Admin only.** YAML import (deferred to T1f — see §6) |
| `GET /v2/templates/export` | **Admin only.** YAML export (deferred to T1f) |

`/shepard/api/...` paths get **no** new endpoints — upstream
clients keep working with raw DataObject CRUD as today.

## 5. The 9 open decisions, settled

From the dispatcher answer earlier, with each resolved:

1. **Template definition shape** → #630 storage + YAML interchange (§1).
2. **Scope** → DataObject-level only for v1. Collection-level + Reference-level templates deferred (rare ask).
3. **Template repository allow-list** → per-Collection `allowedTemplateAppIds` set; empty = all allowed (§3).
4. **Required vs optional fields** → `AttributeSpec.required` field (§2.4).
5. **File requirements** → `FileSlot` with `allowedMimeTypes` (§2.5).
6. **Update propagation** → instances pinned to `templateVersion` at creation; opt-in update action later (§2.3).
7. **Created-from-template relationship** → graph edge `[:CREATED_FROM_TEMPLATE]` + sneaker `templateAppId` attribute on the instance (§2.3).
8. **Permission model** → admin edits `__templates`; Collection owner edits `allowedTemplateAppIds`; users instantiate within the effective allow-list (§3, §4).
9. **Unblocking** → gated on **L2c** so instances reference templates by stable `appId`. L2c is gated on C5. Realistic floor: not before C5 → L2b → L2c.

## 6. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **T1a** | `__templates` Collection auto-created at start; `templateKind` marker attribute; `GET /v2/templates`, `GET /v2/templates/{appId}`. **Read-only**. | S | L2c |
| **T1b** | `AttributeSpec` model + JSON blob storage on template DataObjects. Admin POST/PATCH/DELETE for templates. | M | T1a |
| **T1c** | `FileSlot` model + per-slot upload validation in instantiation. | S | T1b |
| **T1d** | Per-Collection allow-list (`Collection.allowedTemplateAppIds`, the GET / PUT endpoints, owner auth). | S | T1a |
| **T1e** | `POST /v2/collections/{appId}/data-objects/from-template/{templateAppId}` instantiation flow. Required-field validation. `[:CREATED_FROM_TEMPLATE]` edge. UI picker. | M | T1b + T1c + T1d |
| **T1f** | YAML import/export (admin tools). Round-trips templates as portable artifacts. | M | T1b |
| **T1g** | (deferred) "Update instance to latest template version" action. | M | T1e |
| **T1h** | (deferred) Collection-level templates (templates that produce a whole Collection structure, not just a DataObject). | L | T1e |

Recommended order: **T1a → T1b → T1d → T1c → T1e → T1f**. T1d
ships in parallel with T1b/c since it doesn't touch templates
themselves, only the Collection field.

## 7. Migrations

- `V15__Add_appId_constraint_for_template_marker.cypher` — no new
  label (templates are DataObjects); just an index on the
  `templateKind` attribute for query speed: `CREATE INDEX
  template_kind FOR (d:DataObject) ON (d.templateKind)`. Idempotent,
  additive.
- `V16__Add_allowed_templates_to_collection.cypher` — n/a; the new
  property is nullable and lives directly on `Collection`. No
  schema-level migration needed.
- **Tracker rows** in `aidocs/34`:
  - T1a: ZERO (additive Collection at startup; idempotent).
  - T1d: ZERO (additive nullable property on Collection).
  - All others: ZERO (additive endpoints under `/v2/`, no upstream
    surface change).

## 8. RO-Crate integration

When a DataObject was created from a template (`templateAppId` is
set), the RO-Crate export emits a `derivedFrom` link to the
template's RO-Crate metadata. This makes the template lineage
visible to consumers without coupling the export to template-DSL
specifics. Aligns with `aidocs/30` provenance shape.

## 9. Risks

- **Template churn breaks instances.** Mitigated by version pinning
  (§2.3); admins should treat templates as semi-stable schemas.
- **Allow-list confusion.** "Empty set = unrestricted" is a footgun.
  UI shows "Restricted to N templates" or "Unrestricted (all global
  templates allowed)" explicitly; never just shows the raw set.
- **The reserved Collection name `__templates` collides with a
  user-created Collection.** Mitigation: at server start, if a
  collision exists with the wrong shape, refuse to start with a
  clear error pointing at the rename instructions. Idempotent
  start-up data init.
- **Template scope creep.** Users will eventually want
  conditional/computed attributes (e.g. "if `is_fired = true`,
  require `test_report`"). Out of scope for v1; can be a v2
  enhancement of `AttributeSpec`.
- **YAML round-trip drift.** Some shepard-internal fields don't
  round-trip cleanly. Out of scope for v1; T1f's exporter is
  best-effort with explicit warnings on unrepresentable fields.

## 10. Cross-references

- **Issues:** #630 (this design's primary anchor), #483 (User
  groups admin — same admin role applies to templates),
  #647 (Redesign attribute storage in Data Objects — informs the
  AttributeSpec choice).
- **aidocs:** `aidocs/16` L3 (now superseded by this design),
  `aidocs/24` (permission system; admin role + owner role),
  `aidocs/25` (L2c gates this), `aidocs/30` (provenance — `derivedFrom`
  emission), `aidocs/31` (RO-Crate export pickup), `aidocs/34`
  (per-slice ZERO rows).
- **Backlog:** new **T1** umbrella + T1a-T1h sub-IDs in
  `aidocs/16` (replaces / extends the existing L3 row).
