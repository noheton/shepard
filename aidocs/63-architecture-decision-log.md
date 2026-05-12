# 63 — Architecture Decision Log

This document is the **fork-side** ADR log. Decisions taken in the
upstream `architecture/src/09_architecture_decisions/` AsciiDoc bundle
remain authoritative for the original shepard surface; entries here
record decisions that are specific to this fork (the things that show
up in `aidocs/34` and `aidocs/44`).

The intent is to keep a thin, scannable trail of "why we did it this
way" for choices that are otherwise only legible from a diff. Each
entry is short on purpose — if the rationale needs more than a few
lines, it belongs in a dedicated `aidocs/NN-*.md` design doc and the
ADR cites it.

## Index

| ADR | Title | Status | Date | Reversibility |
| --- | --- | --- | --- | --- |
| [ADR-0019](#adr-0019--pre-seed-common-ontologies-default-on) | Pre-seed common ontologies default-on | accepted | 2026-05-12 | easy |

## ADR-0019 — Pre-seed common ontologies default-on

| | |
| --- | --- |
| Date | 2026-05-12 |
| Status | accepted |
| Reversibility | easy — operators flip a single config key |

**Context.** N1a shipped the internal neosemantics ("n10s") repository
(`SemanticRepositoryType.INTERNAL`) so a casual shepard install has
graph-resident SPARQL without an external triple store
(`aidocs/48`). The N1a graph is bare — annotation pickers and SPARQL
queries resolve only against whatever the operator has manually
imported. On a fresh install that's the empty set, which produces the
"useless on day one" UX that N1a was meant to close.

N1b bundles eight common ontologies (PROV-O, Dublin Core, schema.org,
FOAF, QUDT, OM-2, W3C Time, GeoSPARQL — `aidocs/16` N1b row) into the
classpath at `backend/src/main/resources/ontologies/`, with SHA-256
pinning + an idempotent re-import path
(`OntologySeedService`). The remaining decision is the **default
state** of the seed: on or off.

**Decision.** Default the seed **on**. Operators who prefer a bare-n10s
graph flip
`shepard.semantic.internal.preseed-ontologies.enabled=false`;
operators who want most of the bundle but skip a specific entry use
`shepard.semantic.internal.preseed-ontologies.skip-bundles=qudt,om-2`
(CSV).

**Consequences.**

- Casual users get a useful annotation vocabulary on day one — the
  picker and SPARQL surface land with PROV-O activities, schema.org
  types, QUDT units etc. already resolvable.
- Image-size cost is approximately 13 MB at the full-bundle target
  (N1c CLI). The shipped N1b bundle is a minimum-viable stub
  (~16 KB) carrying canonical IRI prefixes and a handful of
  representative terms per ontology; full canonical content lands
  with the N1c refresh CLI.
- Startup cost is one-off per database — n10s deduplicates by IRI
  via the `n10s_unique_uri` constraint, so the post-first-run
  re-import is a no-op (`triplesLoaded == 0`). The seed service is
  fail-soft per bundle: a missing classpath file, SHA mismatch, or
  n10s call error logs at WARN and the next bundle is attempted.
- Operators with a bare-n10s preference flip
  `shepard.semantic.internal.preseed-ontologies.enabled=false` and
  see the same N1a-shipped behaviour they had before.
- Reversibility is easy: the toggle is a single boolean, the seed
  service is the only post-graph-init writer of `:Resource` nodes
  the bundle introduces, and clearing the seeded vocabulary is a
  Cypher one-liner an admin can run against the database.

**Alternatives considered.**

- *Default off, document the toggle.* Rejected — the "casual user
  fresh install" path is exactly the audience N1a was built for;
  shipping it empty wastes the framing.
- *Ship full canonical ontologies in N1b.* Deferred to N1c. The
  ~13 MB image-size cost is acceptable for the UX win, but bundling
  it via the N1c refresh CLI keeps the canonical-content lifecycle
  separate from the seed-mechanism lifecycle — an operator who
  wants the freshest PROV-O doesn't have to wait for a shepard
  release.
- *Operator-time CLI bootstrap only (no startup seed).* Rejected —
  the "no setup" UX is the value here; an operator who has to run
  a CLI before annotations work is already past the casual-user
  cliff.
