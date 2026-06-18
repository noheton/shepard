---
stage: deployed
last-stage-change: 2026-06-18
---

# APISIMP Sweep — fire-115 (2026-06-18)

Scanned `backend/src/main/java/de/dlr/shepard/v2/**` for residual REST surface
sprawl after prior sweeps landed. Four new `@Parameter`-documentation gaps found.

## Findings

### Finding 1 — `APISIMP-VOCAB-BROWSE-SCOPE-UNDOCUMENTED` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/VocabularyBrowseRest.java:179`

`GET /v2/semantic/vocabularies/used-by/{entityAppId}` accepts
`@QueryParam("scope") @DefaultValue("data-object") String scope` with **no
`@Parameter` annotation**. The class Javadoc explains the two valid values
(`data-object` / `collection`) in prose, but the OpenAPI param schema is bare.

The DAO normalises silently:
```java
String normScope = "collection".equalsIgnoreCase(scope) ? "collection" : "data-object";
```
A caller supplying any other string silently gets `"data-object"` behaviour.

**Fix:** add `@Parameter(description="Annotation walk scope. 'data-object' (default): only the entity's own annotations. 'collection': walks [:HAS_DATAOBJECT*0..] descendants too. Any other value treated as 'data-object'.")` to the `scope` param; import `org.eclipse.microprofile.openapi.annotations.parameters.Parameter`; add 1 reflection regression test (`listVocabulariesUsedBy_scopeParamIsDocumented`).

**No runtime change.** AC: OpenAPI schema carries a description for `scope`; `mvn verify -pl backend` green.

---

### Finding 2 — `APISIMP-PREDICATE-STATS-LIMIT-PARAMS-UNDOCUMENTED` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticPredicateStatsRest.java:91-92`

`GET /v2/semantic/predicates/{predicateIriBase64}/stats` accepts:
- `@QueryParam("topValuesLimit") @DefaultValue("20") int topValuesLimit` — no `@Parameter`
- `@QueryParam("sampleLimit") @DefaultValue("10") int sampleLimit` — no `@Parameter`

A caller cannot determine from the OpenAPI schema alone what the defaults are, what the reasonable upper bounds are, or what each param controls.

**Fix:** add `@Parameter(description="Maximum number of top annotation values to return by frequency (default 20).")` and `@Parameter(description="Maximum number of representative annotation samples to return (default 10).")` to the two params; add 2 reflection regression tests.

**No runtime change.** AC: OpenAPI schema carries descriptions for both params; `mvn verify -pl backend` green.

---

### Finding 3 — `APISIMP-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotListRest.java:130-132, 140`

`GET /v2/snapshots` accepts three undocumented `@QueryParam`s:
- `@QueryParam("collectionAppId") String collectionAppId` — no `@Parameter`
- `@QueryParam("page") @DefaultValue("0") int page` — no `@Parameter`
- `@QueryParam("pageSize") @DefaultValue("50") int pageSize` — no `@Parameter`

Additionally, line 140 silently clamps `pageSize`:
```java
int safeSize = Math.min(Math.max(pageSize, 1), 200);
```
The server-side cap of 200 is invisible to callers relying on the OpenAPI schema.

**Fix:** add `@Parameter` annotations to all three params; document the 200-cap on `pageSize`; add 3 reflection regression tests.

**No runtime change.** AC: OpenAPI schema carries descriptions for all three params including the cap; `mvn verify -pl backend` green.

---

### Finding 4 — `APISIMP-PROJECTS-BYANNOTATION-PARAMS-UNDOCUMENTED` (S)

**File:** `backend/src/main/java/de/dlr/shepard/v2/project/resources/ProjectsRest.java:178-181`

`GET /v2/projects/{appId}/{predicate}/{value}` accepts four undocumented `@QueryParam`s:
- `@QueryParam("include") @DefaultValue("identity") String include`
- `@QueryParam("inherit") @DefaultValue("true") boolean inherit`
- `@QueryParam("page") @DefaultValue("0") int page`
- `@QueryParam("pageSize") @DefaultValue("100") int pageSize`

The `inherit` flag has a **special note** in the code (line 192):
```java
// 'inherit' is accepted on the wire but not yet honoured
```
A caller setting `?inherit=false` will see no effect — a latent footgun. This must be documented in the `@Parameter` description.

**Fix:** add `@Parameter` to all four params; for `include` document accepted values (`identity`, `annotations`); for `inherit` add `description` including "accepted but currently ignored — the parent-walk feature is not yet implemented"; add 4 reflection regression tests.

**No runtime change.** AC: OpenAPI schema documents all four params; `inherit` description warns about not-yet-implemented status; `mvn verify -pl backend` green.

## Dispatch plan

| ID | Size | Status |
|---|---|---|
| `APISIMP-VOCAB-BROWSE-SCOPE-UNDOCUMENTED` | XS | dispatched fire-115 |
| `APISIMP-PREDICATE-STATS-LIMIT-PARAMS-UNDOCUMENTED` | XS | queued |
| `APISIMP-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED` | XS | queued |
| `APISIMP-PROJECTS-BYANNOTATION-PARAMS-UNDOCUMENTED` | S | queued |
