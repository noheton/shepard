---
stage: fragment
last-stage-change: 2026-06-16
---

# APISIMP Sweep — 2026-06-16 (fire-64)

Systematic scan of the `/v2` REST surface for residual simplification opportunities.
Scope: `backend/src/main/java/de/dlr/shepard/v2/**` + all `plugins/*/src/main/java/**`.
Executed after APISIMP-MCP-VOCAB-NUMERIC-ARGS (PR #1959) dispatched.

---

## What was checked

1. **Forbidden `/shepard/api/` paths in v2** — `grep @Path.*SHEPARD_API` across v2 + plugins
2. **Numeric OGM id leaks** — `@PathParam/@QueryParam` with `Long/long` types; IO class fields
3. **MCP tool numeric args** — `@ToolArg Long/long` in all MCP tool classes
4. **Per-kind endpoints not unified** — paths like `/v2/data-objects/{id}/<kind>-references` outside the `?kind=` surface
5. **Bespoke admin config REST files** — orphaned `*ConfigRest` outside the generic registry
6. **Bare error responses** — `Response.status(xxx).build()` with no body in 4xx/5xx paths
7. **Pagination param consistency** — `@QueryParam("page"/"size")` vs canonical `page/pageSize`

---

## Findings

### ✅ Clean — no new findings

| Category | Status |
|----------|--------|
| Forbidden `/shepard/api/` paths in v2 | **None found.** Only `v1-compat` plugin intentionally references `SHEPARD_API`. |
| Numeric OGM id `@PathParam/@QueryParam` leaks | **None found.** `SpatialDataPointRest` uses numeric ids but is a tracked frozen v1 resource (`PLUGIN-V2-001`). |
| MCP tool `@ToolArg Long` args (neo4j ids) | **In-flight.** `TimeseriesMcpTools:496,498` (`propertyRepositoryId`/`valueRepositoryId`) fixed by PR #1959 (fire-63, CI ✅). Remaining `Long` args in MCP tools are timestamps/millis, not entity ids. |
| Per-kind endpoints not yet unified | **None new.** `/v2/files` and `/v2/bundles` are tracked under `APISIMP-KIND-DISCRIMINATOR` (L, operator design-verdict blocked). `kind=video`, `kind=git`, `kind=hdf` annotation paths already unified or tracked. |
| Bespoke admin `*ConfigRest` outside registry | **None found.** `AdminConfigRest` properly routes through `ConfigDescriptor`/`ConfigRegistry` after V2CONV-A4+A7. |
| Bare 4xx/5xx responses (no body) | **None found.** All checked. `NO_CONTENT` (204) returns are correct. |
| Pagination `page`/`size` inconsistency | **None.** Canonical standard confirmed: `@QueryParam("page")` + `@QueryParam("pageSize")`. ContainersV2Rest (post-CONT-NS-COLLAPSE) uses this pattern. The `@QueryParam("size")` in ContainersV2Rest:1367 is a thumbnail pixel-size param, not pagination. |

### 📌 Known tracked residuals (not new findings)

| Item | Status |
|------|--------|
| `APISIMP-TYPED-PREDECESSOR-NUMERIC-ID` | ✅ Shipped (PR #1916, merged 2026-06-15). `aidocs/16` row stale → PR #1951 updates it on merge. |
| `APISIMP-MCP-CHANNEL-ANNOT-RESPONSE-ID` | 🔄 PR #1957 in READY queue. |
| `APISIMP-MCP-VOCAB-NUMERIC-ARGS` | 🔄 PR #1959 in READY queue (fire-63). |
| `APISIMP-KIND-DISCRIMINATOR` | ⚠️ Blocked on operator binary-upload pattern verdict. |
| `APISIMP-TSCHANNEL-CONTAINER-ID` | ⏳ Gated on TS-IDb migration. |
| `PLUGIN-V2-001` / `SPATIAL-V6-003` | ⏳ Spatiotemporal `/v2/` sibling shelf; queued. |
| `APISIMP-VIDEO-STREAMREF-PATH` | ⏳ Gated after KIND-DISCRIMINATOR verdict. |
| `APISIMP-GIT-REF-PATH` | ⏳ Gated after KIND-DISCRIMINATOR verdict. |

---

## Summary

**0 new actionable findings filed.** The `/v2` REST surface is converging cleanly:
- All known numeric-id leaks are deprecated, in-flight, or in the READY queue.
- No new forbidden paths, bespoke admin configs, or bare error responses found.
- Only items remaining are gated (KIND-DISCRIMINATOR operator decision) or blocked by downstream migrations (TS-IDb).

Surface health: **high**. The APISIMP campaign has cleared the bulk of the technical debt.
Next sweep warranted after KIND-DISCRIMINATOR design verdict lands (to validate the
`/v2/files` + `/v2/bundles` binary-upload migration).

Filed: `aidocs/16` — no new rows (clean sweep).
