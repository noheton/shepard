---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — Fire 468 — 2026-07-08

**Scope:** All `@Path` REST classes under `/v2/` (backend + plugins); all Response IO classes
for Long/long id fields; bespoke admin config REST not yet on GenericConfigRegistry.

**Coverage:** ~50 `/v2/` REST files + their IO classes. Backlog cross-checked against 247
existing APISIMP entries in `aidocs/16-dispatcher-backlog.md` before reporting each finding.

---

## §A New Findings

### §A1 APISIMP-SSE-SILENT-403-404

**File:** `backend/src/main/java/de/dlr/shepard/v2/events/CollectionEventsRest.java` lines 96–104

**Problem:** `subscribe()` is the sole SSE endpoint in the codebase
(`GET /v2/collections/{collectionAppId}/events`, `void` return type). When the requested
collection does not exist (`ogmId == null`, line 97) or the caller lacks Read permission
(line 102), both branches call `sink.close(); return;`. In Quarkus/RESTEasy, a `void` SSE
method that closes the sink without throwing commits HTTP 200 with an
empty/immediately-closed event stream. Callers that poll `EventSource` see
`readyState = CLOSED` with no error code and cannot distinguish "not found" from
"forbidden" from "no events yet."

The `@Authenticated` class annotation (enforced at framework level before the method body
runs) correctly produces HTTP 401 for unauthenticated callers, making the redundant
null-caller guard at lines 91–94 dead code.

**Fix (XS):** Replace `sink.close(); return;` in the not-found and forbidden branches with
`throw new NotFoundException(...)` and `throw new ForbiddenException()` respectively.
`jakarta.ws.rs.NotFoundException` and `jakarta.ws.rs.ForbiddenException` are already used
elsewhere; RESTEasy maps them to 404/403 before the SSE handshake is established, so the
status code reaches the client. Remove dead-code null-caller guard (lines 91–94) in the
same patch.

**Size:** XS

---

## §B Coverage summary

All other candidate patterns checked and resolved to already-tracked items:

| Pattern checked | Outcome |
|----------------|---------|
| Fake/plain array list responses | All shipped or intentionally kept (e.g. APISIMP-NOTEBOOK-LIST-FAKE-PAGED / fire-368) |
| Bare list responses needing `PagedResponseIO` | All tracked (APISIMP-GIT-CRED-BARE-LIST parked; APISIMP-NOTIF-TRANSPORT-BARE-LIST shipped) |
| Numeric Long fields in IO classes | Tracked (APISIMP-CONTAINERS-PERMS-IO-NUMERIC, APISIMP-ANNOTATION-ALIAS-FIELDS — shipped) |
| Bespoke admin `*ConfigRest` not on registry | None found; thermography + all plugin configs on registry |
| Missing ProblemJson bodies | All shipped (EMPTY-BODIES batches 3–16, 2026-06-13) |
| Inconsistent pagination param names | None found; all v2 paginated endpoints use `?page`+`?pageSize` |
| Boxed Integer params without Bean Validation | None found; all remaining boxed params already tracked |
| SSE / streaming endpoints | **1 finding filed** (§A1 above) |
| New `@Path(Constants.SHEPARD_API + ...)` additions | None found |

The SSE silent-close pattern was not covered by prior EMPTY-BODIES or MISSING-401-RESPONSES
sweeps (those targeted regular `Response`-returning methods), and does not appear anywhere
in the existing backlog.

---

*Sweep run: fire-468 · 2026-07-08*
