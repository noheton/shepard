---
stage: fragment
last-stage-change: 2026-06-12
---

# APISIMP eighth-pass sweep — 2026-06-12

Scope: v2 REST surface + plugin REST layer post-APISIMP-ERROR-ENVELOPE-RESIDUALS
(#1871) and post-PR-#1872 (shapes/render + template-portability + aas-admin error
envelopes, in-flight). Scanned for residual `Map.of()` / `LinkedHashMap`-as-RFC-7807
bodies, un-typed success envelopes, and hand-rolled problem shapes not using the shared
`ProblemJson` record.

## What I found

### Finding 1 — `DataObjectV2Rest` 400 on bad `?fields=` (APISIMP-DO-V2-FIELDS-ERROR-ENVELOPE)

`backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java:196`

```java
return Response.status(Response.Status.BAD_REQUEST)
    .entity(Map.of(
        "title", "Unknown field in ?fields= query parameter",
        "detail", "Field '" + unknown + "' does not exist on DataObjectListItemV2.",
        "status", 400
    ))
    .build();
```

Near-miss: already has `title`/`detail`/`status` but misses `type` and the
`Content-Type: application/problem+json` header. Should be `ProblemJson`.
File: `DataObjectV2Rest.java:196`. **XS** (3 → 1 ProblemJson call).

### Finding 2 — `GitReferenceRest` hand-rolled RFC 7807 (APISIMP-GIT-REF-PROBLEM-TYPE)

`plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java:101`

```java
var problem = new java.util.LinkedHashMap<String, Object>();
problem.put("type", "https://shepard.dlr.de/problems/git.adapter.unsupported-host");
problem.put("title", "No GitAdapter is registered for this host.");
problem.put("status", 501);
problem.put("detail", "v1 ships a GitLab adapter only; GitHub and Gitea ship in G1d.");
return Response.status(Response.Status.NOT_IMPLEMENTED)
    .type("application/problem+json")
    .entity(problem)
    .build();
```

Correctly shaped RFC 7807 but uses a raw `LinkedHashMap` instead of the shared
`ProblemJson` record. Should be 1 `new ProblemJson(type, title, 501, detail, null)`.
**XS** (5-line map → 1 constructor call).

### Finding 3 — `KipResolverRest` `problem()` helper (APISIMP-KIP-PROBLEM-HELPER)

`plugins/kip/src/main/java/de/dlr/shepard/plugins/kip/resources/KipResolverRest.java:220`

```java
private static Response problem(Response.Status status, String type, String title, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("title", title);
    body.put("status", status.getStatusCode());
    body.put("detail", detail);
    return Response.status(status).type("application/problem+json").entity(body).build();
}
```

Private helper reinventing `ProblemJson`. Replace helper body with
`new ProblemJson(type, title, status.getStatusCode(), detail, null)`.
All call sites unchanged. **XS** (4 map.put lines → 1 constructor).

### Finding 4 — `AasShellsRest` disabled-integration response (APISIMP-AAS-SHELLS-DISABLED-ENVELOPE)

`plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:71`

```java
private static Response aasDisabledResponse() {
    return Response.status(501)
        .entity(java.util.Map.of("message",
            "AAS integration is disabled on this instance. ..."))
        .build();
}
```

Not RFC 7807: uses `"message"` key, no `type`, no `Content-Type: application/problem+json`.
Should be `ProblemJson`. Note: `AasAdminRest` in the same plugin is covered by #1872
(APISIMP-AAS-ADMIN-ERROR-ENVELOPE); this is the shells-layer companion. **XS**.

### Finding 5 — `BootstrapRest` untyped 201 success body (APISIMP-BOOTSTRAP-SUCCESS-IO)

`backend/src/main/java/de/dlr/shepard/v2/admin/resources/BootstrapRest.java:57`

```java
return Response.status(Status.CREATED).entity(Map.of("username", username, "role", "instance-admin")).build();
```

Success response (not an error) using `Map.of`. Should be a typed `BootstrapResponseIO`
record so OpenAPI can document the shape. The record has two fields: `username` (String)
and `role` (String, always `"instance-admin"`). **XS** (mint 1 record, update 1 line).

## Not findings (confirmed exceptions)

- `PermissionAuditEntryIO.id` (`long`) — intentionally exposes the Neo4j numeric id for
  admin orphan-audit triage; entities may lack `appId`. Documented exception.
- `ThumbnailRest` `@QueryParam("size")` — semantic pixel-size, not page-size. Correct.
- `SnapshotListRest` / other `?size` QueryParams — covered by APISIMP-PAGINATION-UNIFY
  (PR #1870, dirty; waiting for operator rebase).

## Filed rows

- `APISIMP-DO-V2-FIELDS-ERROR-ENVELOPE` (XS) — `aidocs/16` line ~3574
- `APISIMP-GIT-REF-PROBLEM-TYPE` (XS) — `aidocs/16` line ~3575
- `APISIMP-KIP-PROBLEM-HELPER` (XS) — `aidocs/16` line ~3576
- `APISIMP-AAS-SHELLS-DISABLED-ENVELOPE` (XS) — `aidocs/16` line ~3577
- `APISIMP-BOOTSTRAP-SUCCESS-IO` (XS) — `aidocs/16` line ~3578
