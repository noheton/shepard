---
stage: deployed
last-stage-change: 2026-05-23
---

# OpenAPI documentation standard — LLM-readable

**Status.** Active standard for every new endpoint and every endpoint
touched in any non-trivial way. Captured 2026-05-19 from the user's
ask: *"documentation should be as rich as an LLM with an OpenAPI
wrapper can make sense of it."* Concrete bar: a tool-using LLM that
ingests `/shepard/doc/openapi.json` (no other context) should be able
to (a) decide whether an endpoint applies, (b) compose a valid request
end-to-end, (c) interpret the response shape, and (d) recover from
the documented error states.

## 1. The bar, operationalised

For every operation:

1. **`summary`** — one short sentence in present tense. Auto-prefixed
   with `[v1]` / `[v2]` / `[platform]` by `ShelfTagFilter`. Verb-first
   so the LLM can pattern-match on intent.
2. **`description`** — 4-7 sentences covering:
   - **What** (semantic, not just restating the path).
   - **Auth** (which permission on which entity).
   - **Body shape** (every required field, with an inline `Example: {...}`).
   - **Side effects** (subscriptions, audit, cascading deletes,
     mutated timestamps).
   - **Idempotency** (yes/no, what the dedupe key is).
   - **Cross-references** to the next-step endpoint when this one is
     part of a multi-step flow ("call `POST /v2/collections/{appId}/data-objects`
     next").
3. **Every `@APIResponse`** lists its status code AND the substantive
   condition that produces it. Not `"forbidden"` — `"Caller lacks
   Write permission on the parent Collection (RFC 7807 type
   `/problems/permissions.write-denied`)"`.
4. **Path / query / body parameters** have a `description` line each
   when their name isn't self-evident (`appId` doesn't need it,
   `versionUID` does). For non-string types, give an inline example.
5. **Schemas** that ship a response body get field-level
   `@Schema(description="…", example="…")` on every non-obvious field.
   The `appId` UUID v7 doesn't need it; `revision` does (one-line
   note on what it means).
6. **Errors** that lean on the RFC 7807 shape spell out the `type` URI
   so an LLM can branch on it. The `aidocs/error-types/*.md` registry
   is the canonical list.

## 2. Anti-patterns to avoid

- `description = "Get all foo"` — duplicates the path. LLM gets no
  new information.
- `description = "ok"` on a 200 response. The whole point is the
  *condition* for the 200, not the HTTP semantics.
- Missing example bodies on POST / PATCH. The LLM has no way to know
  which fields are required versus optional from the schema alone if
  the schema marks too many things as `nullable`.
- Hand-rolled prose that drifts from the code. The description should
  reference the entity by its Java type so a reader (human or LLM)
  can grep the codebase to verify.
- Marketing language. "Powerful new endpoint that …" — LLM ignores
  this; humans don't trust it. Stick to mechanics.

## 3. Template for new resources

```java
@POST
@Path("/{collectionAppId}/data-objects")
@Operation(
  summary = "Create a DataObject under a Collection.",
  description =
    "Creates a new DataObject in the Collection identified by " +
    "'collectionAppId'. The server mints 'appId' (UUID v7) and 'id' " +
    "(legacy long) and returns the full entity in 201. \n\n" +
    "Body fields: 'name' (required, non-blank), 'description', " +
    "'attributes' (key/value map, keys must not contain the delimiter " +
    "characters listed in /problems/validation.body), 'status' (one " +
    "of DRAFT/IN_REVIEW/READY/PUBLISHED/ARCHIVED). \n\n" +
    "Example body: {\"name\": \"TR-001\", \"attributes\": {\"campaign\": \"Q3\"}}. \n\n" +
    "Auth: Write on the parent Collection. \n\n" +
    "Side effects: ProvenanceCaptureFilter records a CREATE Activity " +
    "(visible at GET /v2/provenance/entity/{appId}). \n\n" +
    "Next step: POST /v2/data-objects/{appId}/timeseries-references to " +
    "attach data, or PATCH /v2/collections/{cAppId}/data-objects/{appId} " +
    "to update."
)
@APIResponse(
  responseCode = "201",
  description = "DataObject created.",
  content = @Content(schema = @Schema(implementation = DataObjectIO.class))
)
@APIResponse(responseCode = "400",
  description = "Bean Validation failed — name is blank, attribute keys contain delimiter, or status is not one of the enum values.")
@APIResponse(responseCode = "401",
  description = "Authentication required (no JWT or no X-API-KEY).")
@APIResponse(responseCode = "403",
  description = "Caller lacks Write permission on 'collectionAppId'.")
@APIResponse(responseCode = "404",
  description = "No Collection with that appId.")
public Response create(…) { … }
```

## 4. Enforcement

- Reviewers reject new endpoints that don't meet §1.
- Static check candidate: count chars of `@Operation.description` on
  every operation; CI warns if any non-deprecated v2 operation is
  under 200 characters.
- The `ShelfTagFilter` already prefixes summaries; a sister filter
  could later inject the `Next step:` cross-reference markers from
  a graph defined in `aidocs/operations-graph.md` — deferred until
  the manual prose stabilises.

## 5. Scope of the first sweep (this commit batch)

The v2 endpoints I shipped this session — CollectionV2Rest,
DataObjectV2Rest, NotebookRest, VideoStreamReferenceV2Rest,
FileReferenceV2Rest, FileBundleReferenceRest, MePreferencesRest,
G1d check-update — get the §3 treatment. v1 endpoints already got
the §1.2-only treatment in commits `d818dd00` and `501a6a98`.

Subsequent sessions extend the sweep to the rest of `/v2/` and then
the remaining `/shepard/api/` PUT/DELETE leftovers (task #29).
