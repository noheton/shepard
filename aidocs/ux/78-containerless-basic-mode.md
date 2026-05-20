# 78 — Containerless Basic Mode

**Status:** design (task #51, task #42)
**Audience:** frontend contributors, product

---

## Problem

Shepard's current model requires users to understand the Container abstraction
before they can deposit any data. A researcher who wants to "add a timeseries
to my dataset" must:

1. Open the DataObject.
2. Navigate to the Containers tab.
3. Create a TimeseriesContainer.
4. Navigate into the container.
5. Create a channel.
6. Upload or ingest data.

Steps 2–5 are invisible friction for someone who just wants to deposit data.
In basic mode the goal is: **open a DataObject, click "Add data", done.**

---

## Design

### 1. Templates declare expected containers

A template's JSON body gains an optional `containers` hint array:

```json
{
  "containers": [
    { "kind": "timeseries", "name": "Measurements" },
    { "kind": "file",       "name": "Attachments" }
  ]
}
```

The backend already supports this via `T1e` server-side instantiation
(`POST /v2/collections/{c}/data-objects/from-template/{t}`).
Extend `instantiateFromTemplate` to also create the declared containers
immediately, in one server-side step. The frontend never has to touch
container creation when coming from a template.

### 2. "Add data" replaces container creation in basic mode

The DataObject page in basic mode shows a single **Add data** button
(no "Containers" tab, no "Create container" flow). Clicking it:

1. Checks whether the template declared containers (already provisioned).
2. If yes, routes directly to the appropriate upload/ingest dialog for
   the first matching container.
3. If no template / unknown kind, presents a minimal picker:
   "Upload a file" or "Record measurements" — two options, no jargon.

### 3. Container details collapse in basic mode

In basic mode, container-level metadata (appId, stats, settings,
`unsafe delete`) is hidden. The DataObject page shows content inline:
- File list from any FileContainer or SingletonFileReference
- Channel list from any TimeseriesContainer
- Preview widget where available

The container still exists in the graph — it just isn't surfaced.
Advanced mode shows the full container detail as today.

### 4. Containerless payloads for templateless DataObjects

When a DataObject has no template, basic mode offers:

- **Upload a file** → uses `POST /v2/files` (FR1b singleton, no container
  needed) or auto-creates a default FileContainer if it doesn't exist.
- **Record measurements** → prompts for a channel name, auto-creates
  a TimeseriesContainer named "Measurements" + the channel.

Both paths are idempotent: re-clicking "Add data" routes to the
existing container rather than creating a second one.

---

## What stays unchanged

- The backend graph: containers still exist, permissions still attach to
  them, the upstream API surface is untouched.
- Advanced mode: unchanged — full container tab, create/delete, settings.
- The `basic` preference toggle in `useAdvancedMode.ts` is the gate.

---

## Permissions (deferred — bigger story)

Permissions in basic mode are a related but separate design problem.
The current model requires a user to understand Collection-level vs
DataObject-level vs Container-level grants, which is a lot for a
researcher who just wants to share their dataset.

Open questions for a follow-up design (tracked here as a placeholder):

- Should basic mode expose a simplified "share with person / group"
  action that grants sensible defaults (Read on Collection + DataObject
  + all containers) in one click?
- Should template-instantiated DataObjects inherit Collection permissions
  automatically (current behaviour is already close to this)?
- How does basic mode handle the case where a researcher owns a DataObject
  but not the parent Collection?
- Should basic mode hide the permissions tab entirely and route through a
  "Share" button with a simplified dialog?

This placeholder lives here so the containerless UX and the basic-mode
permissions UX are designed together when the time comes, rather than
being bolted on separately.

---

## Implementation sketch

| Step | File(s) | Notes |
|------|---------|-------|
| Backend: extend `instantiateFromTemplate` to create declared containers | `TemplateInstantiationService.java` | Reuse existing container service calls; wrap in one transaction |
| Frontend: hide Containers tab in basic mode | `pages/collections/[id]/dataobjects/[id]/index.vue` | Gate on `!isAdvancedMode` |
| Frontend: "Add data" button | new `AddDataButton.vue` | Inspects DataObject's template containers, routes to the right dialog |
| Frontend: two-option picker for templateless DataObjects | new `AddDataPickerDialog.vue` | "Upload file" → FR1b path; "Record measurements" → TS container auto-create |
| Frontend: auto-create container on first "Record measurements" | extend `useTimeseries*` composables | Check for existing container first; create if absent |

---

## Related

- Task #51 — Basic mode: containerless UX
- Task #42 — "Create from template" prominent for basic users
- `aidocs/ux/` — UX design docs
- `frontend/composables/context/useAdvancedMode.ts` — the basic/advanced toggle
- `T1e` — server-side DataObject instantiation from template
- `FR1b` — singleton FileReference (no container required)
