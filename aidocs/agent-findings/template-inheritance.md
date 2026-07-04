---
name: Template inheritance (TPL-INHERIT) — agent findings
description: Findings from implementing single-parent ShepardTemplate inheritance — chosen edge+flatten shape vs SHACL composition, gate status, deferred items.
type: findings
stage: tests-implemented
last-stage-change: 2026-06-03
---

# Template inheritance (TPL-INHERIT) — findings

## What I built
Single-parent inheritance for `:ShepardTemplate`. A child template carries a
nullable `parentTemplateAppId`; a new `TemplateInheritanceResolver` flattens the
chain (root-first, recursive) into one effective definition — JSON-DSL body,
reference-creation hints (T1e, they live in the body), `iconKey`, and
annotation defaults — with **child entries overriding parent on collision**.

- **Backend:** field on entity + all three IO shapes; resolver (deep object
  merge; positional array merge so `dataobjects[0].attributes` inherits +
  per-key overrides; `shapeGraph` Turtle concatenated parent-ahead-of-child as
  `sh:and`); cycle + kind-match guards on POST/PATCH; `?flatten=true` on GET;
  COW carries the parent edge through; instantiation flattens before extracting
  attributes. `V110` additive-nullable migration + index + rollback twin.
- **Frontend:** `AdminTemplateDialog` parent picker (appId-keyed, same-kind,
  non-retired, self+descendants excluded — UI cycle prevention) + read-only
  inherited-fields preview fetched via `?flatten=true`, shown in basic+advanced.
- **Client:** `parentTemplateAppId` on the two TS models + `flatten` param.

## Chosen shape + why (vs the opposing lens)
A `parentTemplateAppId` **edge + flatten resolver**, not SHACL `sh:node`/`sh:and`
composition. The API-Minimalist lens resists a new edge when shapes already
compose — engaged in design §1.4. The objection only holds if the substrate is
live SHACL; it is not. Templates store **opaque JSON** the backend never reasons
over as RDF (SHACL appears only as an optional frontend-extracted `shapeGraph`
field). Reaching `sh:node` from the DO-generation path would require embedding a
reasoner — verbosity + wrong-layer, dwarfing the one-nullable-field cost. The
edge is the minimal shape AND it *enables* `sh:node`-equivalent graph
concatenation for free. Single-parent (not multiple) matches Java/XSD and dodges
the diamond/linearization problem with no demonstrated authoring need.

## Gates status
- New TPL-INHERIT tests all green: 13 resolver + 5 REST-guard (override
  precedence, cycle rejection, kind-mismatch, flatten merge) JUnit + 8 Vitest
  (cycle exclusion + candidate filtering). `npm run lint` clean on my files;
  `npm run typecheck` passes.
- **Full `mvn verify` could not certify JaCoCo** because the worktree's HEAD has
  ~82 pre-existing surefire failures across ~32 unrelated classes (AI registry
  ordering, CollectionDAO mock counts, `CollectionTemplatesRestTest` Mockito
  matcher misuse, …). Proven pre-existing: those same failures reproduce at the
  parent commit `646d0eca8` before any TPL-INHERIT change. None touch templates.

## Deferred
- Multiple parents (v2 — ordered `parentTemplateAppIds[]` with documented
  precedence) if a real two-base authoring need appears.
- Playwright 4K validation of the editor (the deploy/redeploy + Playwright gates)
  — the worktree's broken baseline blocks a clean redeploy smoke; the picker +
  preview are pure-logic-tested and typecheck-clean.
- Aligning template inheritance to the TPL3 upper-ontology class hierarchy
  (`rdfs:subClassOf`) — that's instance *typing*, a separate concern from
  authoring-time recipe reuse.

## Surprised me
The worktree has a background auto-merge daemon that reverts edits to *tracked*
files between tool calls (new files survive). It forced a workflow of
batch-patch-then-immediately-commit; incremental Edit-tool loops silently lost
work until committed.
