---
stage: concept
last-stage-change: 2026-07-01
---

# APISIMP Sweep — 2026-07-01 (fire-351)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (95 REST resource files) and all plugin `@Path` endpoints against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline:** All named V2CONV-A* and APISIMP-* rows in `aidocs/16` are shipped or blocked (APISIMP-PERMISSION-AUDIT-NEO4J-ID awaiting L2 migration confirmation). No dispatchable named row exists → sweep triggered per CLAUDE.md §1e.

**What SEMANTIC-ANNOTATE-BULK-REST-1 merged this fire:** `POST /v2/annotations/bulk` — 100-row best-effort batch for semantic annotation, `X-AI-Agent` sourceMode propagation, fire-and-forget Activity. No APISIMP impact.

---

## Axes confirmed clean this sweep

| Axis | Verdict |
|---|---|
| Forbidden `@Path(Constants.SHEPARD_API + ...)` additions | ✅ zero found in v2/ or plugins/ (excluding allowlisted spatiotemporal/v1-compat) |
| Per-kind endpoints NOT yet unified under `?kind=` | ✅ none found — ContainersV2Rest + ReferencesV2Rest cover all unified kinds |
| Bespoke admin `*ConfigRest` not on generic registry | ✅ V2CONV-A4/A7 fully shipped; all feature configs on `/v2/admin/config/{feature}` |
| Numeric Neo4j id leaks in `@PathParam`/`@QueryParam` | ✅ `ContainersV2Rest Long start/end` are epoch-ns timestamps, not node ids (confirmed); only known leak = PermissionAuditEntryIO (already tracked APISIMP-PERMISSION-AUDIT-NEO4J-ID, blocked) |
| Pagination param name consistency | ✅ `page` + `pageSize` used consistently across all 38+ paginated list endpoints |
| Error envelope consistency | ✅ all 4xx/5xx using `problem()` pattern or `@APIResponse` problem+json schema |
| Endpoints superseded by `POST /v2/shapes/render` | ✅ ThermographyV2Rest already dissolved (V2CONV-A7-THERMO-REST-DISSOLVE); no other superseded endpoints found |
| Response fields with zero callers | ✅ `DataObjectDetailV2IO` / `DataObjectListItemV2IO` already strip numeric ids via `@JsonIgnoreProperties`; no other bloated response shapes found |

---

## Findings (3 new rows filed)

### F1 — APISIMP-MISSING-OPERATIONID-P3 (MINOR, XS)

**What:** Three REST resource files lack `operationId` in their `@Operation` annotations, breaking generated client method naming and OpenAPI spec completeness. Prior wave APISIMP-MISSING-OPERATIONID (fire-341, PR #2213) fixed 22 sites but missed these three:

| File | Endpoint | Gap |
|---|---|---|
| `MappingsMaterializeRest.java:95` | `POST /v2/mappings/{templateAppId}/materialize` | No `@Operation` at all |
| `UserAvatarByAppIdRest.java:41` | `GET /v2/users/{appId}/avatar` | `@Operation(summary=...)` but no `operationId` |
| `UserAvatarRest.java:63,113` | `PUT /v2/users/me/avatar`, `DELETE /v2/users/me/avatar` | `@Operation(summary=...)` but no `operationId` on 2 methods |

**Fix:** Add `operationId` to each: `"materialize"`, `"getUserAvatar"`, `"uploadMyAvatar"`, `"deleteMyAvatar"`. Annotation-only; zero wire change.

**AC:** All 4 method sites have `operationId` in the OpenAPI spec; `mvn verify -pl backend` green.

---

### F2 — APISIMP-DQR-ORPHAN (MINOR, S)

**What:** `CollectionDQRRest` exposes `GET/POST/DELETE /v2/collections/{collectionAppId}/dqr` (Data Quality Requirements — a rule engine for flagging DataObjects that don't meet schema/completeness constraints). Grep across frontend composables, pages, components, MCP tools, e2e scripts, and examples finds **zero callers** anywhere. The endpoint is fully implemented backend-side (`DataQualityRequirementService`, `DataQualityRequirementDAO`, `:DataQualityRequirement` Neo4j entity). The V2CONV-A7-SURVEY explicitly classified it as "UNUSED-NOT-SUPERSEDED — zero callers anywhere, but neither is superseded by a generic surface → out of scope for this consolidation pass; separate dead-feature decision."

**Decision needed (operator):** Ship a FE stub + add to admin nav (promoting to `alpha`) OR decommission (delete REST resource + DAO + entity + migration; if data exists, provide migration). Not a pure-API-simplification call — requires product direction.

**Filed as decision row.** Dispatcher will NOT decommission without operator confirmation.

**AC (if ship-FE path):** `PlaceholderFragmentPane` stub in DataObject/Collection detail; `GET /v2/collections/{appId}/dqr` called with v2 client; backlog row `PLACEHOLDER-DQR-UI` filed.
**AC (if decommission path):** REST resource deleted; `DROP CONSTRAINT`/`DETACH DELETE :DataQualityRequirement` migration filed; aidocs/34 BREAKING entry.

---

### F3 — APISIMP-LEDGER-ANCHOR-ORPHAN (MINOR, S)

**What:** `LedgerAnchorRest` exposes `POST /v2/admin/ledger/anchor`, `GET /v2/admin/ledger/anchor/{jobId}`, and `GET /v2/admin/ledger/data-objects/{appId}/ledger-anchors` (a cryptographic anchoring/ledger system). Grep across all frontend, MCP, e2e, example, and CLI code finds **zero callers**. The V2CONV-A7-SURVEY noted "`/v2/admin/ledger/anchor` (LedgerAnchorRest, probe → 405) → UNUSED-NOT-SUPERSEDED" as a dead-feature candidate.

**Decision needed (operator):** Same shape as F2 — ship operator UI or decommission. If ship: admin hub tile + `PLACEHOLDER-LEDGER-UI` backlog row. If decommission: REST + service deleted; aidocs/34 BREAKING entry if any `:LedgerJob` nodes exist in production.

**AC (if ship path):** Admin panel tile calls `POST /v2/admin/ledger/anchor` on demand; job status poll via `GET /v2/admin/ledger/anchor/{jobId}`.
**AC (if decommission path):** REST deleted; any `:LedgerJob` purge migration; aidocs/34 BREAKING.

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-MISSING-OPERATIONID-P3 | MINOR | XS | queued — dispatch next fire |
| APISIMP-DQR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-LEDGER-ANCHOR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |

**Recommended next dispatch:** APISIMP-MISSING-OPERATIONID-P3 (XS, no operator input needed, pure annotation fix, test-compile only).
