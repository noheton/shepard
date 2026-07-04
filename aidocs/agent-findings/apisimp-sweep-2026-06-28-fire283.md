---
stage: deployed
last-stage-change: 2026-06-28
---

# APISIMP sweep — fire-283 (2026-06-28)

Scope: all `/v2/` REST resources in `backend/src/main/java/de/dlr/shepard/v2/**`
plus plugin `@Path`s. Prior fires (280–282) completed the plugin surface and the
main pagination wave. This fire focuses on any remaining residual sprawl (hard caps,
plain-list aggregation endpoints, bespoke non-registry configs).

## Surfaces checked

| Resource | Path | Verdict |
|---|---|---|
| `ReferencesV2Rest` | `GET /v2/references` (list by kind+dataObject) | ✅ naturally bounded per-entity, no pagination needed |
| `ReferencesV2Rest` | `PUT /v2/references/{appId}` + `PUT /v2/references/{appId}/content` | ✅ mutation, no list |
| `InstanceRegistryRest` | `GET /v2/admin/instances` | ✅ single-item singleton envelope |
| `LedgerAnchorRest` | `GET /v2/admin/ledger/data-objects/{appId}/ledger-anchors` | ✅ Phase 1 skeleton (returns 501); feature-gated off by default |
| `PublicationsListRest` | `GET /v2/{kind}/{appId}/publications` | ✅ intentionally unbound, explicitly justified in Javadoc ("entities rarely accumulate more than a handful of Publication rows") |
| `SnapshotListRest` | `GET /v2/snapshots` | ✅ paginated (`page` default 0, `pageSize` default 50, cap [1,200]) |
| `ShepardTemplateRest` | `GET /v2/templates/tags?kind=` | ⚠️ Finding 1 — filed as row 3829 |
| `VocabularyBrowseRest` | `GET /v2/semantic/vocabularies/used-by/{entityAppId}` | ✅ bounded per-entity (few vocabularies per annotation set) |
| `JupyterConfigPublicRest` | `GET /v2/jupyter/config` | ✅ single-item |
| Admin config descriptors | `GET /v2/admin/config/{feature}` | ✅ all feature configs on generic registry |
| Plugin credential endpoints (DataCite, EPIC, Unhide) | `/v2/admin/minters/*/credential` | ✅ acceptable bespoke (credential management, not list surfaces) |

## §Finding 1 — APISIMP-TEMPLATE-TAGS-CAP (row 3829) — XS

**Location:** `backend/src/main/java/de/dlr/shepard/template/daos/ShepardTemplateDAO.java:238`

**Problem:** `listDistinctTags(String templateKind)` executes:
```cypher
MATCH (t:ShepardTemplate) WHERE (t.retired IS NULL OR t.retired = false)
[AND t.templateKind = $kind]
UNWIND coalesce(t.tags, []) AS tag RETURN DISTINCT tag ORDER BY tag
```
No `LIMIT` clause. The result is returned directly as `Response.ok(dao.listDistinctTags(kind)).build()` in `ShepardTemplateRest.tags()` (line 302). There is no server-side cap and no contract bounding the list size.

In practice: distinct tags across all non-retired templates are a controlled vocabulary, bounded by the number of unique tag strings operators have entered. In a mature deployment this could reach several hundred; in an extraordinary case (bulk-import of templates from many domains) it could grow unboundedly.

**Fix (PR #2149):**
- Add ` LIMIT 500` to the Cypher string at `ShepardTemplateDAO.java:238`
- No pagination params needed — distinct-tag autocomplete does not need paging at 500-item granularity
- Add 1 new test verifying the cap (seed >500 distinct tags, assert exactly 500 returned)
- Update `@APIResponse` description to document the 500-item cap

## Status

| Row | Title | Size | Status |
|---|---|---|---|
| 3829 | APISIMP-TEMPLATE-TAGS-CAP | XS | ⏳ queued (fire-283) |

## Sweep completeness

The v2 core REST surface (backend + plugins) is now fully swept through fire-283.
No unbounded list endpoints remain without either a server cap, pagination, or an
explicit documented justification. The only remaining open item is the XS cap on
`GET /v2/templates/tags` (row 3829).
