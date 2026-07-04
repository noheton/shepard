---
stage: fragment
last-stage-change: 2026-06-16
---

# APISIMP Sweep — 2026-06-16 (fire-71)

Systematic scan of the `/v2` REST surface for residual simplification opportunities.
Scope: `backend/src/main/java/de/dlr/shepard/v2/**` + all `plugins/*/src/main/java/**`.
Executed after PLACEHOLDER-FS1e1-TESTS (PR #1960) dispatched — fire-71.

---

## What was checked

1. **Forbidden `/shepard/api/` paths in v2** — `@Path(Constants.SHEPARD_API + ...)` in v2 + plugins
2. **Numeric OGM id leaks** — `@PathParam/@QueryParam` with `Long/long`; IO class fields; MCP `row.put("id", ...)` emissions
3. **MCP tool numeric id responses** — all `row.put(...)` calls across all MCP tool classes
4. **Per-kind endpoints not unified** — paths like `/v2/data-objects/{id}/<kind>-references` outside the `?kind=` surface
5. **Bespoke admin config REST files** — orphaned `*ConfigRest` outside the generic registry
6. **Bare error responses** — `Response.status(xxx).build()` with no body in 4xx/5xx paths
7. **Pagination param consistency** — `@QueryParam("page"/"size")` vs canonical `page/pageSize`

---

## Findings

### Finding 1 — MCP response numeric id leak: CollectionMcpTools (NEW)

**Severity:** MINOR  
**Files:** `backend/src/main/java/de/dlr/shepard/v2/mcp/CollectionMcpTools.java`

`CollectionMcpTools` emits numeric Neo4j OGM id alongside `appId` in two tool responses:

| Line | Tool | Emission |
|------|------|----------|
| 90 | `list_collections` | `row.put("id", c.getId())` — numeric OGM id |
| 155 | `list_data_objects` | `row.put("id", item.getId())` — numeric OGM id |

The `appId` (UUID v7) is already present at lines 89 and 154 respectively. The tool description for `list_data_objects` (line 108) also advertises `"id (long)"` as a documented field, directing callers toward the numeric id.

The fire-64 sweep checked `@ToolArg Long` arg types but missed `row.put("id", ...)` response emissions in non-arg positions. Fire-61 sweep caught this same pattern in `TimeseriesMcpTools` (PR #1957, READY). This is the same pattern in two more tools.

**Fix:** Remove `row.put("id", ...)` at lines 90 and 155; update the `list_data_objects` tool description to drop the `"id (long)"` field mention.  
**Wire break (MCP only):** Callers reading `"id"` from `list_collections` or `list_data_objects` must switch to `"appId"`. Pre-production — no known production caller used the numeric id through this path.

**Filed as:** `APISIMP-MCP-RESPONSE-IDS-2` (combined with Finding 2 below).

---

### Finding 2 — MCP response numeric id leak: LabJournalMcpTools (NEW)

**Severity:** MINOR  
**Files:** `backend/src/main/java/de/dlr/shepard/v2/mcp/LabJournalMcpTools.java`

`LabJournalMcpTools.toRow()` (line 210) emits `row.put("id", e.getId())` — numeric Neo4j OGM id — in the shared helper used by all lab journal list/get tool responses.

```java
// line 209
row.put("appId", e.getAppId());
// line 210  ← remove this
row.put("id", e.getId());
```

`e.getAppId()` (UUID v7) already uniquely identifies the entry at line 209.

**Fix:** Remove line 210.  
**Wire break (MCP only):** MCP callers reading `"id"` from lab journal tool responses must switch to `"appId"`. Same pre-production posture.

**Filed as:** `APISIMP-MCP-RESPONSE-IDS-2` (combined with Finding 1).

---

### ✅ Clean — no new findings in other categories

| Category | Status |
|----------|--------|
| Forbidden `/shepard/api/` paths in v2 | **None found.** Only `v1-compat` plugin references `SHEPARD_API`. |
| Numeric OGM id `@PathParam/@QueryParam` leaks | **None new.** `SpatialDataPointRest` uses numeric ids (tracked frozen v1, `PLUGIN-V2-001`). `ContentMcpTools:388` resolves parent OGM id internally for permission check — not emitted on wire. |
| Per-kind endpoints not yet unified | **None new.** `/v2/files`, `/v2/bundles`, `kind=video`, `kind=git` all tracked under existing gated rows. |
| Bespoke admin `*ConfigRest` outside registry | **None found.** `AdminConfigRest` routes through `ConfigDescriptor`/`ConfigRegistry` cleanly. |
| Bare 4xx/5xx responses (no body) | **None new.** `ContainersV2Rest` thumbnail/presign empty-bodies already covered by PR #1955 (READY). |
| Pagination `page`/`size` inconsistency | **One remaining:** `UserGroupV2Rest:88` `@QueryParam(Constants.QP_SIZE)` covered by PR #1954 (READY). `ContainersV2Rest:1367` `?size` is thumbnail pixel-size, not pagination — correct. |

### 📌 Known tracked residuals (not new — for completeness)

| Item | Status |
|------|--------|
| `APISIMP-MCP-CHANNEL-ANNOT-RESPONSE-ID` | 🔄 PR #1957 READY — same pattern as findings above; timeseries-specific |
| `APISIMP-MCP-VOCAB-NUMERIC-ARGS` | 🔄 PR #1959 READY — arg types fixed; same theme |
| `APISIMP-KIND-DISCRIMINATOR` | ⚠️ Blocked on operator binary-upload pattern verdict |
| `APISIMP-TSCHANNEL-CONTAINER-ID` | ⏳ Gated on TS-IDb migration |
| `APISIMP-VIDEO-STREAMREF-PATH` + `APISIMP-GIT-REF-PATH` | ⏳ Gated after KIND-DISCRIMINATOR verdict |
| `PLUGIN-V2-001` / `SPATIAL-V6-003` | ⏳ Spatiotemporal `/v2/` sibling shelf; queued |

---

## Summary

**2 new actionable findings filed** as a single XS row `APISIMP-MCP-RESPONSE-IDS-2`.  
Pattern: MCP tools emitting numeric `row.put("id", ...)` alongside `appId` — same class of issue as PR #1957 (timeseries) and PR #1959 (arg types), applied to Collection and LabJournal tool responses.

After PRs #1957 and #1959 merge, this row will be the last remaining `row.put("id", ...)` numeric id leak across all MCP tools.

Surface health: **high**. One XS cleanup row filed; all other categories clean. Dispatch `APISIMP-MCP-RESPONSE-IDS-2` next fire.
