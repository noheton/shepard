---
stage: deployed
last-stage-change: 2026-06-15
---

# APISIMP Sweep — fire-61 (2026-06-15)

Scope: v2 REST surface + MCP tool surface. Focuses on residual numeric-id
leaks and per-kind per-DO path residuals after fire-60 filed
KIND-DISCRIMINATOR (L, blocked).

## Sweep method

1. Scanned all `@Path` annotations in `backend/src/main/java/de/dlr/shepard/v2/`
   and `plugins/*/src/main/java/de/dlr/shepard/v2/` for per-kind per-DO patterns
   not yet tracked.
2. Scanned `backend/src/main/java/de/dlr/shepard/v2/mcp/` for numeric id leaks
   in MCP tool responses and arguments.
3. Cross-referenced `SemanticAnnotationIO` and `AnnotationMcpTools.list_vocabularies`
   to confirm mismatch between what `list_vocabularies` returns and what
   `create_channel_annotation` accepts.

## Findings

### Finding 1 — APISIMP-MCP-CHANNEL-ANNOT-RESPONSE-ID (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/mcp/TimeseriesMcpTools.java`

Two MCP tool response builders emit the numeric Neo4j OGM id under the key
`"id"` instead of the UUID v7 appId under `"appId"`:

```java
// list_channel_annotations — line 465
row.put("id", io.getId());      // getId() → Long (OGM numeric id)

// create_channel_annotation — line 514
row.put("id", resultIO.getId()); // same
```

`SemanticAnnotationIO` has both `private Long id` (line 18, OGM) and
`private String appId` (line 21, UUID v7). The fix is a 2-line swap:

```java
row.put("appId", io.getAppId());
row.put("appId", resultIO.getAppId());
```

CLAUDE.md rule: "MCP tool arguments accept `appId` strings only." The same
principle applies to MCP tool responses — a caller must be able to use the
returned identifier to drive a subsequent MCP call, and those calls take
`appId` strings.

**AC:** `list_channel_annotations` and `create_channel_annotation` responses
contain `"appId"` (UUID v7) not `"id"` (numeric); `mvn verify -pl backend`
green.

---

### Finding 2 — APISIMP-MCP-VOCAB-NUMERIC-ARGS (M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/mcp/TimeseriesMcpTools.java`  
**Lines:** 496–498

`create_channel_annotation` declares two MCP tool arguments as `Long`:

```java
@ToolArg(description = "Neo4j OGM id of the vocabulary that owns the property IRI.
    Get from `list_vocabularies`.") Long propertyRepositoryId,
@ToolArg(description = "Neo4j OGM id of the vocabulary that owns the value IRI.
    Get from `list_vocabularies`.") Long valueRepositoryId,
```

But `AnnotationMcpTools.list_vocabularies` returns `row.put("appId", v.getAppId())`
(`AnnotationMcpTools.java:98`) — UUID v7 strings, not OGM longs. A caller
who follows the tool description ("Get from `list_vocabularies`") receives a
string `appId` but the tool demands a numeric `Long`. The call will always
fail with a type mismatch.

`SemanticRepositoryDAO.findByAppId(String appId)` already exists (line 52),
so the resolution path is available.

**Fix shape (M):**
1. Change both `@ToolArg` params from `Long propertyRepositoryId, Long valueRepositoryId`
   to `String propertyVocabAppId, String valueVocabAppId`.
2. Inject `SemanticRepositoryDAO` into `TimeseriesMcpTools` (CDI @Inject).
3. Resolve: `long repoId = semanticRepositoryDAO.findByAppId(propertyVocabAppId).getId()`.
4. Pass `repoId` into `io.setPropertyRepositoryId(repoId)`.
5. Update `@ToolArg` descriptions to say "UUID v7 of the Vocabulary (appId from
   `list_vocabularies`)."

**AC:** `create_channel_annotation` MCP args are String (UUID v7); `list_vocabularies`
→ appId → `create_channel_annotation` flow works end-to-end without type error;
`mvn verify -pl backend` green.

---

### Finding 3 — APISIMP-VIDEO-STREAMREF-PATH (M)

**File:** `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2Rest.java`  
**Line:** 62

```java
@Path("/v2/data-objects/{dataObjectAppId}/video-stream-references")
```

This is the old per-kind, per-DO path pattern that 191 §2 dissolves.
`VideoStreamReferenceKindHandler` exists at `plugins/video/.../handlers/`
for the `kind=video` slot — CRUD (list/get/create/update/delete) must
migrate to the unified `/v2/references?kind=video` surface via the handler.

The binary download path (`GET /{appId}/download` at line 151 of the same
file) is binary-content territory excluded from the unified JSON surface
— this sub-path becomes a companion endpoint per KIND-DISCRIMINATOR design.

**Scope of this row:** CRUD migration only; binary download endpoint is
tracked under APISIMP-KIND-DISCRIMINATOR.

**AC:** `/v2/data-objects/{dataObjectAppId}/video-stream-references` CRUD paths
return 404; `GET|POST|PATCH|DELETE /v2/references?kind=video` work via the
kind handler; binary download path migrated to `/v2/references/{appId}/content`
companion; `mvn verify` (full incl. plugins) + FE typecheck green.

---

### Finding 4 — APISIMP-GIT-REF-PATH (M)

**File:** `plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java`  
**Line:** 47

```java
@Path("/v2/data-objects/{dataObjectAppId}/git-references")
```

Same per-kind per-DO path pattern as video. `GitReferenceKindHandler`
exists at `plugins/git/.../handlers/` for the `kind=git` slot — CRUD
migrates to the unified `/v2/references?kind=git` surface.

Action sub-paths (e.g. `preview`, `check-update` if present) are
action endpoints, not CRUD — migrate to `/v2/references/{appId}/preview`,
`/v2/references/{appId}/check-update` (sub-path pattern established by
`TimeseriesAnnotationRest`).

**AC:** `/v2/data-objects/{dataObjectAppId}/git-references` CRUD paths
return 404; `GET|POST|PATCH|DELETE /v2/references?kind=git` work via
the kind handler; action sub-paths live under `/v2/references/{appId}/…`;
`mvn verify` (full incl. plugins) + FE typecheck green.

---

## Summary

| Row | Size | Status |
|-----|------|--------|
| APISIMP-MCP-CHANNEL-ANNOT-RESPONSE-ID | XS | filed → queued, dispatch next fire |
| APISIMP-MCP-VOCAB-NUMERIC-ARGS | M | filed → queued |
| APISIMP-VIDEO-STREAMREF-PATH | M | filed → queued (gated after KIND-DISCRIMINATOR design) |
| APISIMP-GIT-REF-PATH | M | filed → queued (gated after KIND-DISCRIMINATOR design) |

No XS/S-sized code slices were found beyond APISIMP-MCP-CHANNEL-ANNOT-RESPONSE-ID.
That row is the next dispatch target (fire-62).

## Residuals deferred (known, already tracked)

- `SpatialDataPointRest.java:51` — frozen upstream-compat; tracked as
  APISIMP-V1-PATH-RESIDUAL-1 (deferred/compat surface).
- `VideoStreamReferenceV2Rest.java:140,143,145,210,212,215` — exception
  handlers with bare string bodies; already shipped as APISIMP-VIDEO-STORAGE-EXCEPTION-TYPE.
- APISIMP-KIND-DISCRIMINATOR (`/v2/files`, `/v2/bundles`) — L-sized, blocked on
  operator binary-upload verdict (3 options in 191 §2).
