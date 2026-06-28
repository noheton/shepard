---
title: Work with AAS
---

# Working with AAS (Asset Administration Shell)

shepard speaks IDTA AAS v3. Your Collections become AAS Shells; your DataObjects
become Submodel references. Use any AAS-aware client (Eclipse BaSyx, AASX Package
Explorer, BaSyx SDK) to browse your data.

---

## Browse in the Shepard UI

The **Tools → AAS Shells** tile (top navigation bar) opens the shells listing.
From there you can navigate without leaving the browser:

1. **Tools → AAS Shells** — lists every Collection you can read as an AAS Shell.
2. Click a Shell name → **Shell detail page** — shows Asset Information and the
   Submodels table (one row per top-level DataObject). Each row has an
   **"Open DataObject"** button that jumps directly to the DataObject detail view.
3. Click a Submodel name → **Submodel detail page** — shows the Submodel
   identity fields and a **SubmodelElements (Properties)** table listing all
   semantic annotations on that DataObject as AAS Property elements.

In-context shortcuts are also available on every entity detail page:

- **Collection detail → AAS pane** (right sidebar) — shows the Shell IRI,
  idShort, Asset Kind, Submodel count, and a **"View AAS Shell"** button.
- **DataObject detail → AAS pane** (right sidebar) — shows the Shell IRI and
  Submodel IRI with **"Open AAS Shell"** and **"Open Submodel"** buttons.

---

## Browse your data as AAS Shells (API)

Point your AAS client at:

```
GET /v2/aas/shells
```

Each Collection you can read appears as one Shell. The Shell's `id` is
`urn:shepard:collection:{collectionAppId}` and its `submodels` list
references the top-level DataObjects.

---

## Find what this shepard exposes

```
GET /v2/aas/.well-known/aas-server
```

No authentication required. Returns a capability document listing the AAS
profile, shell count, and bundled IDTA templates.

---

## Register this shepard with an AAS registry

Set these environment variables (or application properties) before starting
the backend:

```
shepard.aas.registry.url=https://registry.example.org
shepard.aas.registry.api-key=<your-bearer-token>   # omit for open registries
shepard.aas.base-url=https://shepard.example.org   # your public URL
```

On next startup the AAS plugin seeds `PENDING` outbox rows for every
Collection and pushes them to the registry. Check status:

```
GET /v2/admin/aas/registrations
```

Trigger a manual retry if some rows are `FAILED`:

```
POST /v2/admin/aas/registrations/sync
```

---

## Import IDTA Submodel Templates

shepard bundles three IDTA templates: Digital Nameplate, Technical Data,
and Time Series Data. Import them once after install:

```http
POST /v2/admin/aas/import-idta-templates
Authorization: Bearer <admin-token>
```

The import is idempotent — re-running it is safe.

After import the templates appear in the Templates section and can be used
when creating DataObjects.
