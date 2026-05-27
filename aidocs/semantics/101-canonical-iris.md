---
stage: feature-defined
last-stage-change: 2026-05-27
---

# 101 — Canonical IRI namespaces for Shepard semantic documents

**Status.** Frozen — namespace bases must not change without a coordinated
migration.  
**Snapshot date.** 2026-05-27.  
**Audience.** Contributors authoring `.ttl`, SHACL shapes, or JSON-LD contexts;
CI pipeline maintainers.

Closes backlog item **VIEWS-AS-SHAPES-CANONICAL-IRIS** (see
`aidocs/16-dispatcher-backlog.md`).

---

## 1. Purpose

Three RDF namespace prefixes appear across every Shepard/MFFD semantic
document, every shipped SHACL shape, and every JSON-LD context file.
Before this document existed, two of the three prefixes had diverged
between files (e.g. `shepard:` appeared as both
`http://semantics.dlr.de/shepard#` and
`http://semantics.dlr.de/shepard-upper#`; `fair2r:` as both
`https://noheton.github.io/f-ai-r/ns#` and
`https://noheton.org/f-ai-r/ns#`). Diverged namespaces mean an IRI
that looks the same to a human is treated as a different resource by an
RDF processor — a silent correctness failure that becomes expensive to
untangle at scale.

This document freezes the three canonical bases. Every new `.ttl` file,
SHACL shape, JSON-LD context, or SPARQL query **must** use these prefixes
and no others. The CI lint (`scripts/lint-ttl-namespaces.sh`) enforces
this at PR time.

---

## 2. Canonical namespace table

| Prefix | Canonical base IRI | Owner | Notes |
|---|---|---|---|
| `mffd:` | `http://semantics.dlr.de/mffd-process#` | DLR ZLP Augsburg | MFFD process ontology — process steps, parameters, constraints, defect types |
| `shepard:` | `http://semantics.dlr.de/shepard-upper#` | DLR (this fork) | Shepard upper model — `shepard:Collection`, `shepard:DataObject`, `shepard:Activity`, `shepard:User`, etc. |
| `fair2r:` | `https://noheton.org/f-ai-r/ns#` | f(ai)²r project | AI provenance vocabulary — `fair2r:Claim`, `fair2r:AuthoringPass`, `fair2r:Prompt`, `fair2r:sourceMode` |

### Why these bases?

- **`mffd:`** — `semantics.dlr.de` is the DLR institutional namespace for
  semantic artefacts from ZLP Augsburg. The `mffd-process` path component
  aligns with the file name `mffd-process.ttl` in
  `examples/mffd-showcase/ontology/`.
- **`shepard:` (`shepard-upper`)** — the `-upper` suffix distinguishes
  the cross-cutting upper model (which every plugin and shape compiles
  against) from any future per-domain extension. The previous
  `semantics.dlr.de/shepard#` variant (without `-upper`) predates this
  fork's upper-ontology design (`aidocs/semantics/96`) and is retired.
- **`fair2r:`** — `noheton.org` is the canonical domain for the
  f(ai)²r provenance vocabulary. The `github.io` variant that appeared
  in `99-promptlog-design.md` was a draft-time alias that was never
  intended to be durable.

---

## 3. Known prior divergences (retired)

The following non-canonical forms appeared in documents before this
freeze. They are recorded here so a `grep` search produces a clear
explanation.

| Non-canonical form | File | Status |
|---|---|---|
| `@prefix shepard: <http://semantics.dlr.de/shepard#>` | `aidocs/semantics/95-shacl-templates-and-individuals.md` | Retire on next edit of that doc (`.md` not linted — see §4) |
| `PREFIX fair2r: <https://noheton.github.io/f-ai-r/ns#>` | `aidocs/semantics/99-promptlog-design.md` | Tracked under backlog item FAIR4-NS-FREEZE; same shape of fix |

No `.ttl` file in the repo contains a non-canonical form as of
2026-05-27. The lint keeps it that way.

---

## 4. Scope of the CI lint

The lint script (`scripts/lint-ttl-namespaces.sh`) covers:

- All `*.ttl` files under `aidocs/` and
  `backend/src/main/resources/`.
- All `*.ttl` files under `examples/` (if added).

It does **not** lint `.md` files (prose examples in docs are
human-readable illustrations, not machine-parsed RDF). Markdown
namespace drift is tracked manually and corrected on edit.

SPARQL queries (`.sparql`, `.rq`) and JSON-LD contexts (`.jsonld`) are
not covered by the TTL lint but should follow the same prefix table.

---

## 5. What to do when adding a new `.ttl` file

1. Copy the prefix declarations from any existing shape file in
   `backend/src/main/resources/shapes/` — they already use the
   canonical bases.
2. Run `scripts/lint-ttl-namespaces.sh` locally before pushing.
3. Do not add `@prefix example: <http://example.org/>` — use a
   `@prefix ex: <http://semantics.dlr.de/shepard-examples#>` stub
   namespace for any worked-example individuals if needed.

---

## 6. Plugins with diverged namespaces

Any plugin that already published IRIs under a non-canonical base must
ship `owl:sameAs` bridges in a one-time migration TTL before merging
into the main shapes catalogue. The pattern:

```turtle
# one-time sameAs bridge — plugin migration example
@prefix mffd-old: <http://example.org/mffd-old#> .
@prefix mffd:     <http://semantics.dlr.de/mffd-process#> .

mffd-old:SomeClass owl:sameAs mffd:SomeClass .
```

The bridge TTL is uploaded via the admin ontology endpoint and removed
after the migration window (90 days, or after all data has been
re-annotated with canonical IRIs).

---

## 7. Cross-references

- `aidocs/semantics/95-shacl-templates-and-individuals.md` — shipped
  shapes; all use the canonical `shepard:` base.
- `aidocs/semantics/96-upper-ontology-alignment.md` — upper-ontology
  design that establishes `shepard:` as the cross-cutting prefix.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.9` — the
  section that first committed to this sibling document.
- `aidocs/semantics/contexts/mffd-context.jsonld` — the JSON-LD
  context that maps the MFFD vocabulary; uses `mffd:` and `shepard:`
  as declared here.
- `scripts/lint-ttl-namespaces.sh` — the CI enforcement script.
- `aidocs/16-dispatcher-backlog.md` — backlog items
  VIEWS-AS-SHAPES-CANONICAL-IRIS and FAIR4-NS-FREEZE.
