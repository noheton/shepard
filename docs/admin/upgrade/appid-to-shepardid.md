---
layout: default
title: Upgrading from appId to shepardId
description: Operator guide for the appId → shepardId field rename on v2 API responses and request bodies
audience: admin
permalink: /admin/upgrade/appid-to-shepardid/
---

# appId → shepardId upgrade guide

This guide covers the planned rename of the `appId` JSON field to `shepardId`
across all `/v2/` API responses and request bodies. The rename affects operator
scripts, client libraries, Jupyter notebooks, and any downstream integration that
reads `/v2/` responses verbatim.

**Status (2026-05-27):** This rename is _planned_ but _not yet shipped_. The current
`/v2/` surface emits `appId` only. When the dual-emit window opens (ID-MIG1), both
`appId` and `shepardId` will be present simultaneously in responses. When the cutover
ships (ID-MIG2), `appId` will be removed from `/v2/` responses.

The `/shepard/api/...` (v1) surface is byte-frozen per the API-version policy and
will **never** see this rename.

---

## What is changing

The `appId` field (a UUID v7 string, minted once per entity at creation time) is
being renamed to `shepardId` at the JSON wire level on all `/v2/` endpoints. The
**value does not change** — only the key name moves. Every entity retains its existing
UUID.

---

## Timeline

| Phase | What ships | What to do |
|---|---|---|
| **Today** | `appId` only in `/v2/` responses | No action needed |
| **ID-MIG1 — dual-emit** | Both `appId` and `shepardId` present in `/v2/` responses; `Deprecation: true` + `Sunset: <date>` response headers appear | Start updating clients to read `shepardId`; both fields work |
| **ID-MIG2 — cutover** | `appId` removed from `/v2/` responses; `shepardId` is the only key | All clients must use `shepardId` |

The dual-emit window gives operators a grace period (exact dates will be set when
ID-MIG1 ships). The `/shepard/api/...` surface is not affected at any phase.

---

## Field mapping

Every IO class that currently emits `appId` on the `/v2/` surface will rename the
field. The key classes are:

| Class | Location | Notes |
|---|---|---|
| `BasicEntityIO` | `de.dlr.shepard.common.neo4j.io` | Base class inherited by `CollectionIO`, `DataObjectIO`, and most entity shapes |
| `DataObjectSummaryIO` | `de.dlr.shepard.v2.dataobject.io` | Compact DataObject reference in list responses |
| `DataObjectDetailV2IO` | `de.dlr.shepard.v2.dataobject.io` | Full DataObject detail; also carries `timeseriesReferenceAppIds`, `fileBundleReferenceAppIds` arrays |
| `ContainerSummaryIO` | `de.dlr.shepard.v2.collection.io` | Container reference inside Collection responses |
| `CollectionPropertiesIO` | `de.dlr.shepard.v2.collection.io` | `/v2/collections/{appId}/properties` response |
| `FileBundleReferenceIO` | `de.dlr.shepard.v2.bundle.io` | File bundle reference shape |
| `FileReferenceV2IO` | `de.dlr.shepard.v2.file.io` | Singleton file reference |
| `AnnotationIO` | `de.dlr.shepard.v2.annotations.io` | Semantic annotation responses |
| `ActivityIO` | `de.dlr.shepard.v2.provenance.io` | Provenance activity records |
| `MirroredUserIO` | `de.dlr.shepard.v2.admin.users.io` | Mirrored user entries in admin responses |
| `LabJournalRevisionIO` | `de.dlr.shepard.v2.labjournal.io` | Lab journal revision entries |
| `NotebookReferenceIO` | `de.dlr.shepard.v2.labjournal.io` | Notebook reference entries |
| `VocabularyIO` | `de.dlr.shepard.v2.vocabularies.io` | Semantic vocabulary entries |
| `ShepardTemplateIO` | `de.dlr.shepard.v2.template.io` | Template entries |
| `SemanticConfigIO` | `de.dlr.shepard.v2.admin.semantic.io` | Admin semantic configuration |
| Plugin IO classes | Various `plugins/*/io/` | All plugin IO classes that extend `BasicEntityIO` inherit the rename automatically |

In addition, `appId` appears in URL path parameters (e.g. `/v2/collections/{appId}`)
and in `ImportJobResultIO.CreatedEntityIO`. URL path parameter names are not changing
— only the response body field name moves.

---

## Migration checklist

Use this checklist when ID-MIG1 (dual-emit) ships to audit your client code:

1. **Grep for `"appId"` in client code**

   ```bash
   grep -r '"appId"' your-project/
   grep -r '\.appId' your-project/
   grep -r "\.appId" your-project/
   ```

   Every hit that reads from a `/v2/` response body is a migration candidate.

2. **Grep for TypeScript/Python client bindings**

   If you use the generated backend-client (TypeScript) or a custom Python client:

   ```bash
   grep -r 'appId' your-project/src/ --include="*.ts"
   grep -r 'appId' your-project/ --include="*.py"
   ```

3. **Check Jupyter notebooks**

   ```bash
   grep -r 'appId' notebooks/
   ```

4. **Check import scripts that read `/v2/` export shapes**

   The RO-Crate export and JSON-LD shapes use `appId` as a field key today.
   Review any script that parses export artifacts.

5. **Check MCP tool integrations**

   Any Claude/MCP agent tool that reads `appId` from a Shepard response body will
   need updating when the cutover ships.

During the dual-emit window both `appId` and `shepardId` will be present — you can
update clients incrementally without a flag-day deployment.

---

## What does NOT change

- **`/shepard/api/...` (v1) responses** — never affected. Byte-frozen per the
  API-version policy.
- **The UUID value itself** — same UUIDs, same v7 sort order, same uniqueness
  guarantees. Nothing in the Neo4j graph changes.
- **URL path parameters** — `/v2/collections/{appId}` path templates keep the word
  `appId` in the parameter name (these are URL templates, not JSON keys).
- **`BasicEntityIO.id`** — the legacy Neo4j internal long ID field is unrelated to
  this rename.

---

## See also

- [`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md)
  — change ledger row ID-MIG4 covers this rename
- [`aidocs/16-dispatcher-backlog.md`](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md)
  — ID-MIG1 through ID-MIG5 backlog chain
- [`docs/admin/upgrade/`]({{ '/admin/upgrade/' | relative_url }}) — upgrade index
