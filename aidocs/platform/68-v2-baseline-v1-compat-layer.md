# V2 baseline + `/shepard/api/` as compat layer — Design (V2BASE)

> **SUPERSEDED by `aidocs/platform/103-v1-compat-plugin-extraction.md`
> (Phase 2) + `aidocs/platform/103a-v1-compat-marker-plugin.md`
> (Phase 1)** — 2026-05-22. The two-phase plugin route in #103 / #103a
> reaches the same end-state by a cleaner path. #68's package-rename
> proposal becomes one possible implementation tactic inside #103
> Phase 2 if useful, not its own design. Retained here for historical
> reference and for the upstream-policy discussion that motivated the
> v1/v2 split; do not implement against this document.

**Scope.** Forward-looking design note for backlog item **V2BASE**:
flip this fork's API surface posture. Today the upstream
`/shepard/api/...` paths are the implementation and the `/v2/...` shelf
is additive on top. After this design lands, `/v2/...` becomes the
implementation and `/shepard/api/...` becomes a thin compatibility
adapter over it.

**Status.** Design, not started. The most concrete inflection point
is the existing L2 chain in `aidocs/platform/25` (the `appId`-based
identifier migration), which already pre-positions both shelves to
share the same backend services.

**Snapshot date.** 2026-05-18.

**Companion docs.** Sits next to `aidocs/platform/25` (identifier
migration), `aidocs/34` (upstream upgrade ledger), `aidocs/44`
(feature matrix). When V2BASE lands, every `/shepard/api/...` row in
`aidocs/44` gains a note "v1 = thin adapter over v2".

---

## 1. Why now

Two forcing functions:

1. **The compat layer is implicit today.** Every `/v2/...` endpoint
   reaches into the same service layer as the matching
   `/shepard/api/...` endpoint, but the wiring is bespoke per
   endpoint pair. This works but means every new feature has to
   touch both shelves carefully. As `/v2/...` grows, the duplicate
   wiring grows too.
2. **A real-world compat-layer test arrives soon.** The
   `home-showcase` MQTT collector (`project_home_showcase_mqtt.md`)
   uses the upstream Python `shepard_client` which only speaks
   `/shepard/api/...`. Once that collector runs against this fork's
   backend continuously, the compat layer is on the critical path
   for telemetry ingest. That's exactly the smoke test the inversion
   needs.

The user's framing (2026-05-18):

> "regarding v1 and v2 api compatibility. consider v2 baseline, v1 is
> just a layer over v2."

> "as shepard time series collector only speaks v1 - great test for
> backward compatibility layer"

---

## 2. End-state shape

```
┌─────────────────────────────────────────────┐
│             HTTP / JAX-RS                   │
├──────────────────────────┬──────────────────┤
│  /shepard/api/...        │  /v2/...         │
│  (V1CompatRest classes)  │  (V2 Rest)       │
│  ──→ delegates to ──→    │  ──→ services ──→│
└──────────────────────────┴──────────────────┘
                  │                  │
                  ▼                  ▼
            ┌─────────────────────────────┐
            │  Backend services           │
            │  (CollectionService,        │
            │   DataObjectService, …)     │
            └─────────────────────────────┘
                  │
                  ▼
            ┌─────────────────────────────┐
            │  DAOs + Neo4j / Mongo / TS  │
            └─────────────────────────────┘
```

Today both shelves call services directly. Tomorrow the
`/shepard/api/...` layer is a single-purpose adapter that:

- Accepts upstream's request shape (path params, query params,
  body schema).
- Translates request fields where they differ from V2 (e.g. the
  Long `id` path-param → `appId` lookup).
- Calls the V2 implementation.
- Translates the response back to upstream's wire shape (e.g.
  drops new V2 fields the upstream schema doesn't carry).

The point of the inversion is that **future development happens
on V2**. V1 follows only as wire compatibility demands.

---

## 3. What stays / what moves

### Stays where it is

- The service layer (`*Service.java` classes under
  `de/dlr/shepard/{context,data,auth,common}/`). These are
  already shape-neutral.
- The DAO layer. L2c already retired Long-id-only queries in favour
  of `appId` lookups via `EntityIdResolver`.
- The Neo4j entities and Mongo / Timescale schemas — V2BASE is
  purely an HTTP-layer refactor.
- The `:Activity` provenance stamping (PROV1a). The capture filter
  runs at the JAX-RS layer and tags both shelves the same way.

### Moves

- The current `/shepard/api/...` `*Rest.java` classes (e.g.
  `CollectionRest`, `DataObjectRest`, `FileReferenceRest`,
  `TimeseriesRest`, `StructuredDataRest`) become "compat-adapter"
  classes. Suggested rename: `de.dlr.shepard.compat.v1.*Rest`.
  Wire path stays exactly the same; the Java package moves.
- For every upstream endpoint, the request handling moves into a
  V2 sibling under `de.dlr.shepard.v2.*Rest`. Where a V2 sibling
  already exists (the additive ones we've shipped this year), the
  V1 adapter just delegates. Where no V2 sibling exists yet, this
  refactor creates one with `/v2/...` as the canonical path.
- The OpenAPI surface gets clearer per-shelf separation. Existing
  `quarkus.smallrye-openapi.path-filter` / tag groupings already
  hint at this; V2BASE makes the two shelves first-class in the
  OpenAPI doc.

### Goes away (after a deprecation window)

- The L2e migration (`aidocs/34`) already drops `/v1/` long-id
  paths after a deprecation window. V2BASE accelerates that —
  once the upstream surface is officially a compat layer,
  upstream clients are explicitly the supported migration target
  and their lifecycle becomes operator-policy, not
  implementation-coupling.

---

## 4. Concrete migration plan

Three landings, each independent:

### Phase A — package move + import sweep (mechanical)

1. Move every existing `/shepard/api/...` REST class from its
   current package to `de.dlr.shepard.compat.v1.*`.
2. Update `@Path` strings to still resolve to
   `/shepard/api/{collections|dataObjects|...}` — the path stays;
   only the Java package moves.
3. Re-run the test suite. Nothing should change semantically.
4. Update `aidocs/34` ledger row "Phase A landed".

**Risk:** zero — pure package rename.

### Phase B — extract V2 siblings for upstream-only endpoints

For each upstream endpoint that lacks a `/v2/...` sibling today:

1. Create the matching `/v2/...` REST class at the canonical
   path (kebab-case, `appId` path params, RFC 7396 patch where
   applicable).
2. Move the request-handling body into the V2 class.
3. The V1 adapter retained from Phase A becomes a thin
   delegation:

   ```java
   @POST
   public Response createCollection(@Valid CollectionIO body) {
     // V1 wire — Long-id path, upstream Permissions shape
     CollectionIO v2Response = v2CollectionRest.create(adaptInbound(body));
     return Response.ok(adaptOutbound(v2Response)).build();
   }
   ```
4. Where the adapt step is non-trivial (long-id → appId lookup,
   field shape changes), the adapter calls a small
   `de.dlr.shepard.compat.v1.adapt.*Adapter` helper.

**Risk:** medium — every endpoint pair needs a regression test
covering the upstream wire shape. Reuse the existing upstream
client (Python / TS) integration tests as the contract.

**Per-endpoint sub-tasks** track in `aidocs/16` as
`V2BASE.B.<surface>` rows.

### Phase C — `/v2/...` becomes the primary documented surface

1. Update `docs/architecture.md` ("API shelves" section) to flip
   the framing: `/v2/...` is the implementation, `/shepard/api/...`
   is the compat adapter.
2. Update `docs/user-guide.md` examples to use `/v2/...`.
3. Update the generated polyglot clients (Python first — that's
   what the home-showcase collector consumes) to default to
   `/v2/...`. The legacy upstream client keeps working against
   `/shepard/api/...`; both ship from the same OpenAPI document
   with two base-path generation runs.
4. Operator runbook in `aidocs/34` gains a "Compat-layer
   posture" section explaining that upstream clients are now
   officially supported via the adapter (with a soft
   deprecation date for the operators who want to flip
   themselves to V2 deliberately).

**Risk:** documentation-only.

---

## 5. Concrete pre-flight cleanups

Things to fix BEFORE Phase A so the refactor doesn't carry forward
known cruft:

1. **C5 + C5b** (Cypher-injection) — landed. No action.
2. **L2c** (DAO `appId` lookups) — landed. No action.
3. **L2d** (`PermissionsService.isAllowed` segment dispatch +
   `Neo4jQueryBuilder` search-JSON id predicates) — **must land
   first**. V2BASE assumes the permission lookups speak `appId`
   end-to-end.
4. **Pagination coverage backfill** — Phase A is the right time
   to extend pagination to the 65 unpaginated list endpoints
   (`project_open_queue_2026-05-18.md` item 8). The new V2 siblings
   should ship with cursor pagination from day one; the V1
   adapters preserve the legacy page/size shape.
5. **OpenAPI per-shelf filter** — verify the existing tag groups
   render cleanly when both shelves are in the same JAR. Quarkus
   3.27's `quarkus.smallrye-openapi.path-filter` may need a tweak.

---

## 6. Validation: the MQTT collector as the integration smoke

Once Phase B lands for the timeseries endpoints, the
`home-showcase` MQTT collector becomes the production-grade
smoke test:

- The collector ingests live HA telemetry through
  `POST /shepard/api/timeseriesContainers/{id}/payload`.
- That endpoint, post-refactor, is a V1 compat adapter calling
  `POST /v2/timeseries-containers/{appId}/payload`.
- A 24-hour run with no data loss confirms the adapter is byte-
  identical at the request side.
- Restart the collector pointing at `/v2/...` directly. Same
  behaviour proves the V2 surface is the real implementation.

No fixture test substitutes for this — the upstream client's
exact request shape (headers, encoding quirks, retry semantics)
is the contract.

---

## 7. Risks and counter-arguments

**Counter:** "the inversion is cosmetic". Wire-level behaviour is
the same; what changes is which package the file lives in.

Rebuttal: the value isn't in renaming files. It's in flipping
the **future-cost gradient**:

- Today: every new feature must consider `/shepard/api/` parity
  by default. New endpoints either go to `/v2/` (this fork's
  current default per `CLAUDE.md`) or get bespoke compat wiring.
- Post-V2BASE: every new feature is built on `/v2/` natively. If
  it must also be reachable via `/shepard/api/`, the adapter is a
  ~20-line file that delegates. The default is no adapter — i.e.
  upstream gets nothing new, which matches the API freeze.

**Counter:** "the L2 chain hasn't finished yet (L2d, L2e
outstanding)". V2BASE shouldn't start before L2d.

Rebuttal: agreed. Phase A is gated on L2d landing.
(`aidocs/16` already tracks L2d as a queued item.)

**Counter:** "splits ownership across two REST class trees,
doubling the surface to maintain". True for endpoints that need
non-trivial adaptation; trivial pass-through adapters cost ~20
lines.

Net: ownership burden goes UP for the duration of Phase B, then
DOWN forever after as new features stop carrying compat-shaped
debt.

---

## 8. Out of scope

- **Renaming the wire path itself.** `/shepard/api/...` stays
  `/shepard/api/...` forever (per the standing API freeze) —
  what changes is the implementation side.
- **Deprecating the upstream client.** Both the upstream and
  fork-specific clients remain supported indefinitely; the
  upstream client keeps shipping from upstream's own OpenAPI
  document, untouched by this work.
- **Changing the OpenAPI document layout for the upstream
  shelf.** The V1 wire is frozen — operators that pin their
  generated client to a specific snapshot must keep working
  unchanged.
