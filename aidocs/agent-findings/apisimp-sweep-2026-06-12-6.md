---
stage: fragment
last-stage-change: 2026-06-12
---

## APISIMP Sweep pass 9 — 2026-06-12

Scope: v2 REST surface + plugin endpoints.  
Prior sweep: `apisimp-sweep-2026-06-12-5.md` (5 findings, APISIMP-BOOTSTRAP-SUCCESS-IO in-flight PR #1877).

### Finding1 — FILE-MIGRATION-ROLLBACK-IO: FileMigrationRest.rollback() returns raw Map instead of typed IO

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/storage/FileMigrationRest.java:159`

**Problem:** `PATCH /v2/admin/storage/migrate/{appId}/rollback` (or equivalent per-file rollback endpoint) responds with `Response.ok(java.util.Map.of("appId", appId, "status", "ROLLED_BACK"))` on the success path. The other endpoints in this resource — `GET /v2/admin/storage/migrate/status` and `POST /v2/admin/storage/migrate` — both return typed IO records (`FileMigrationStateIO`). The rollback success response is undocumented in OpenAPI (`@APIResponse` has no `content` annotation), and the `status` field value `"ROLLED_BACK"` is an implicit enum-string with no schema.

**Fix:** Introduce `FileMigrationRollbackResultIO` record (`appId: String`, `status: String`) in `backend/.../v2/admin/storage/io/`; replace the `Map.of(...)` entity with `new FileMigrationRollbackResultIO(appId, "ROLLED_BACK")`; add `@Content(schema=@Schema(implementation=FileMigrationRollbackResultIO.class))` to the `@APIResponse`.

**AC:** `PATCH .../rollback/{appId}` response body is a typed `FileMigrationRollbackResultIO` documented in OpenAPI; `mvn verify` green.

**Size:** XS

---

### Finding2 — PROV-COUNT-IO: ProvenanceRest.countActivities() returns raw Map instead of typed IO

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:353`

**Problem:** `GET /v2/provenance/count` returns `Response.ok(java.util.Map.of("count", c)).build()` where `c` is a `long`. The `@APIResponse(responseCode = "200", description = "Row count.")` has no `content` schema, so OpenAPI clients can't know the response shape. The parallel `NotificationRest.getUnreadCount()` endpoint uses the correct typed pattern — `Response.ok(new NotificationCountIO(unread)).build()` — with a schema-documented `NotificationCountIO` record. The provenance count endpoint pre-dates that pattern and was never upgraded.

**Fix:** Introduce `ActivityCountIO` record in `backend/.../v2/provenance/io/` (`count: long`); replace `Map.of("count", c)` with `new ActivityCountIO(c)`; add `@Content(schema=@Schema(implementation=ActivityCountIO.class))` to the `@APIResponse`.

**AC:** `GET /v2/provenance/count` response body is a typed `ActivityCountIO` documented in OpenAPI; `mvn verify` green.

**Size:** XS

---

### Finding3 — MAPPINGS-MAT-PROBLEM-HELPER: MappingsMaterializeRest private problem() helper uses HashMap instead of ProblemJson

**File:** `backend/src/main/java/de/dlr/shepard/v2/mappings/resources/MappingsMaterializeRest.java:263–274`

**Problem:** The bespoke `problem(ResponseStatusType, String, String)` / `problem(int, String, String)` helpers at lines 263–274 build a `HashMap<String, Object>` with `"error"` and optional `"code"` keys and return it with `Response.status(status).entity(entity).build()`. This is not RFC 7807: the response lacks `Content-Type: application/problem+json`, the `"type"` URI field, and the `"title"` field. Every other v2 resource that had a bespoke `problem()` helper was migrated to `ProblemJson` in prior sweeps (APISIMP-KIP-PROBLEM-HELPER, APISIMP-SHAPES-RENDER-ERROR-ENVELOPE). `MappingsMaterializeRest` was missed because it does not import `ProblemJson` — the helper was written in parallel. All callers within the class (lines 126, 130, 139, 153, 175, 182, 185, 260) use the helper; the fix is two-line helper rewrite + one new import.

**Fix:** Import `de.dlr.shepard.common.exceptions.ProblemJson`; rewrite both `problem()` overloads to return `Response.status(status).type("application/problem+json").entity(new ProblemJson(code != null ? code : "transform.error", error, resolvedStatus, error, null)).build()`; remove the `import java.util.HashMap` and `import java.util.Map` lines that are only used by these helpers (the `Map.of()` at line 161 uses the unambiguous `Map.of()` for `inputReferenceAppIds` which is a request parameter, not a response body — keep `Map` import if needed for that).

**AC:** All `POST /v2/mappings/{templateAppId}/materialize` error paths return `Content-Type: application/problem+json` with `type`, `title`, `status`, `detail` fields; no raw `HashMap` response; `mvn verify` green.

**Size:** XS

---

### Finding4 — NOTIF-TEST-DELIVERY-IO: NotificationAdminRest transport-test 200 response is still a raw Map

**File:** `backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationAdminRest.java:161`

**Problem:** `POST /v2/admin/notifications/transports/{appId}/test-delivery` returns `Response.ok(Map.of("status", "delivered", "transport", transport.getKind())).build()`. APISIMP-NOTIF-TEST-RESP-ENVELOPE (shipped 2026-06-12) upgraded the response from a plain string `"delivered via <kind>"` to this two-field Map. The Map itself is still untyped: there is no `@APIResponse content` schema annotation on the endpoint, so OpenAPI clients see an undocumented 200 body. The `"transport"` value is the raw `kind` string (transport type discriminator) with no documented enum constraint.

**Fix:** Introduce `NotificationTestDeliveryIO` record in `backend/.../v2/notifications/io/` (`status: String`, `transport: String`); replace `Map.of("status", "delivered", "transport", transport.getKind())` with `new NotificationTestDeliveryIO("delivered", transport.getKind())`; add `@Content(schema=@Schema(implementation=NotificationTestDeliveryIO.class))` to the `@APIResponse(responseCode="200")` annotation.

**AC:** `POST /v2/admin/notifications/transports/{appId}/test-delivery` 200 response is documented in OpenAPI with typed `NotificationTestDeliveryIO` body; `mvn verify` green.

**Size:** XS
