---
name: APISIMP sweep pass 10
stage: fragment
last-stage-change: 2026-06-12
---

# APISIMP Sweep Pass 10 — 2026-06-12

## Findings

### APISIMP-NTF-PROBLEM-RESPONSES — NotificationAdminRest missing @Content for error responses
Severity: MAJOR
File: backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationAdminRest.java:77-79
Current: 
```java
@APIResponse(responseCode = "404", description = "transportId does not resolve.")
@APIResponse(responseCode = "502", description = "Transport returned failure.")
@APIResponse(responseCode = "503", description = "No sender registered for this transport kind.")
```
All error responses (404, 502, 503) lack `@Content(schema = @Schema(implementation = ProblemJson.class))` annotations, making OpenAPI schema generation incomplete. The implementation returns RFC-7807 ProblemJson bodies (see line 136: `Response.status(Response.Status.NOT_FOUND).build()` and line 144, 158, 165 `problem()` calls), but the schema is undocumented.
Fix: Add `@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))` to each error @APIResponse annotation, consistent with AdminConfigRest pattern (lines 100-104).
AC: 
- POST /v2/admin/notifications/test now documents all RFC-7807 error response schemas
- OpenAPI spec includes problem+json schema for 404, 502, 503 status codes

---

## Findings: summary
1 finding filed:
- APISIMP-NTF-PROBLEM-RESPONSES (MAJOR): NotificationAdminRest missing @Content for error responses

