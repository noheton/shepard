---
stage: fragment
last-stage-change: 2026-06-16
---

# APISIMP Sweep — 2026-06-16 (fire-73)

Systematic scan of the `/v2` REST surface for residual simplification opportunities.
Scope: `backend/src/main/java/de/dlr/shepard/v2/**` + all `plugins/*/src/main/java/**`.
Executed after fire-72 (APISIMP-MCP-RESPONSE-IDS-2 → PR #1961 dispatched).

---

## What was checked

1. **Forbidden `/shepard/api/` paths in v2** — `@Path(Constants.SHEPARD_API + ...)` in v2 + plugins
2. **Numeric OGM id leaks** — `@PathParam/@QueryParam` with `Long/long`; IO class fields; MCP `row.put("id", ...)` emissions
3. **MCP tool numeric id responses** — all `row.put(...)` calls across all MCP tool classes
4. **Per-kind endpoints not unified** — paths outside the `?kind=` surface
5. **Bespoke admin config REST files** — orphaned `*ConfigRest` outside the generic registry
6. **Bare error responses** — `Response.status(xxx).build()` with no body in 4xx/5xx paths
7. **Pagination param consistency** — `@QueryParam` names vs canonical `page/pageSize`
8. **Plugin namespace allowlist** — plugin-owned `/v2/<namespace>` not in {jupyter, aas} allowlist

---

## Findings

### Finding 1 — ContainersV2Rest empty 401/404 bodies (already in flight)

**Severity:** MAJOR  
**File:** `ContainersV2Rest.java` (thumbnail, presigned-upload, commit-upload, presigned-download endpoints)  
**Status:** **ALREADY IN FLIGHT — PR #1955 (APISIMP-CONTAINERS-PRESIGN-EMPTY-BODIES), READY for orchestrator merge.**  
Not a new finding.

### Finding 2 — SpatialPromoteRest at `/v2/spatial` (already reviewed + approved)

**Severity:** MAJOR (if unreviewed)  
**File:** `plugins/spatiotemporal/src/main/java/de/dlr/shepard/v2/spatial/promote/SpatialPromoteRest.java`  
**Status:** **ALREADY REVIEWED AND APPROVED** in `aidocs/agent-findings/plugin-v2-only-audit.md` (row 30):
> "`spatiotemporal (v2 promote)` | **fixed-here** | `/v2/spatial/promote` (SpatialPromoteRest, SPATIAL-UNIFY-004) — appId-keyed in-context action, returns unified `ReferenceV2IO`. Distinct capability, no generic seam covers it."

This is an intentional exception documented by the plugin-v2-only audit. Not a new finding.

---

## Verdict

**0 genuinely new findings.** The `/v2` REST surface continues to be in good shape after the APISIMP series. All remaining known issues are either:
- In-flight PRs awaiting orchestrator merge (#1951–#1961, all 10 READY)
- Gated on design decisions (KIND-DISCRIMINATOR, TS-IDb)
- Pre-approved exceptions (SpatialPromoteRest, UnhideFeedRest)

No new `APISIMP-*` rows filed this fire.

---

## Next action

Fall through to priority 6 (UI VERIFICATION + DUAL-DOC): dispatch **DOC-ADV-admin-config** — write `docs/reference/admin-config.md` for the V2CONV-A4 generic admin config registry (`/v2/admin/config/{feature}`). Shipped feature, no reference page.
