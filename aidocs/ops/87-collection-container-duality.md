# 87 — Collection / Container Duality: Design Discussion

**Status.** Living design doc — initial draft 2026-05-17.
**Audience.** Contributors, product / UX, marketing.
**Relates to.** `aidocs/58 §5` (CP1 ContainerProperties), `aidocs/42 §2` (data model),
`aidocs/ops/85` (UI overhaul), `aidocs/16` (backlog).

---

## 1. The Concept — Why Two Things?

shepard has **two organisational primitives**:

| Primitive | Neo4j label | What it is |
|---|---|---|
| **Collection** | `:Collection` | A research context — a campaign, an experiment, a project. Owns DataObjects and their metadata. Carries permissions, lab journal, semantic annotations. |
| **Container** | `:FileContainer`, `:TimeseriesContainer`, etc. | A data store with a stable URL. Holds the actual payload bytes / time-series channels. Has its own lifecycle (can outlive or predate any Collection). |

A **DataReference** (FileReference, TimeseriesReference, …) is the edge that joins them:
a DataObject inside a Collection points at data stored in a Container.

```
Collection ──owns──► DataObject ──refers to──► Container (stable endpoint)
                       │
                       └── also carries: lab journal, semantic annotations,
                                         relationships, git references, …
```

### Why not just one thing?

The separation exists for two concrete reasons:

**Reason A — Views.** The same Container data can appear in multiple Collections
with different metadata, different annotations, and different access rules.

```
 FileContainer /sensors/climate-lab-A
      │
      ├──► DataObject "climate 2024" in Collection "Wing-A Test Campaign"
      │         (annotations: test regime, phase, anomaly flags)
      │
      └──► DataObject "climate 2024" in Collection "Facility Maintenance Log"
               (annotations: sensor calibration events, drift flags)
```

Both Collections share one source of truth. No duplication of raw data. Each
Collection adds its own semantic layer. This is the core marketing argument:
**no duplicate data, no out-of-band data loss**.

**Reason B — Static endpoints for collectors.** A timeseries collector
(e.g. `shepard-timeseries-collector`, a LabVIEW VI, a Raspberry Pi logger)
needs a single stable URL to POST to. If that URL lived inside a Collection,
every new experiment would require reconfiguring the collector.

A Container URL (`/timeseries-containers/{id}`) is stable across campaigns.
The collector never knows about Collections. A Collection simply references
the Container's channels when it needs them.

```
LabClimate collector  ──POST──►  TimeseriesContainer /lab-climate
                                      │
                        Campaign A ──►└── TimeseriesReference in DataObject "env"
                        Campaign B ──►└── TimeseriesReference in DataObject "env"
                        Campaign C ──►└── TimeseriesReference in DataObject "env"
```

One certified collector. Three campaigns. Zero per-campaign reconfiguration.

---

## 2. Why Users Find It Hard

Research by the UI working group and direct user sessions identify four pain
points:

| # | Pain | Root cause |
|---|---|---|
| P1 | "Why do I have to go to Containers to find my data?" | Top-level nav treats Containers as a peer of Collections — two separate worlds |
| P2 | "I created a collection but my file isn't visible there" | Users add files to a FileContainer but forget to create a FileReference binding it to a DataObject |
| P3 | "What is a Container? I just want to upload a file." | The Container abstraction is invisible for the simple case (one experiment, one file) |
| P4 | "Why does my data live in two places?" | The split is not explained in the UI — no tooltip or help text at the critical moment |

The `default-filecontainer` feature was a band-aid for P3: auto-create a
FileContainer when a Collection is created so casual users don't have to think
about it. It solved P3 but made P1 and P2 worse (the hidden container still
exists and shows up in the Containers list, confused as a separate copy).

---

## 3. What the Ideal UX Looks Like

The goal is to **hide the Container concept for casual users** while keeping
it fully accessible for power users and collectors.

### 3.1 Casual user path (upload a file to an experiment)

```
User opens a Collection
  → sees DataObject tree
  → clicks "Add file" on a DataObject
  → drags or picks a file
  → file is uploaded to the Collection's default-filecontainer automatically
  → FileReference appears on the DataObject immediately
```

The Container is invisible. The UX is "upload a file to my experiment."
For this path, the existing `default-filecontainer` approach is correct —
it just needs better naming and UI polish.

### 3.2 Power user path (reference existing data)

```
User opens a Collection
  → expands a DataObject
  → clicks "Link existing data"
  → a picker opens showing available Containers (with search)
  → user picks a Container and selects channels/files
  → a DataReference is created pointing to the selected Container
```

This path is already possible; it's just buried in the modal stepper
(aidocs/ops/85 pain point P2).

### 3.3 Collector setup path

```
Operator creates a TimeseriesContainer via API or admin UI
  → records the container's stable ID/URL in the collector's config
  → collector streams data independently of any Collection
  → when a new experiment starts, a researcher creates a Collection
    and adds a DataObject with a TimeseriesReference pointing to the
    existing container
```

This path needs no UI changes — it is already supported. The only gap is
discoverability: researchers don't know to look for existing Containers to
link to.

---

## 4. Proposed Changes

### 4.1 Navigation (UI decision)

**Decision (from aidocs/ops/85 §7):** containers should be surfaced inside the
collection sidebar as a second tree, **not** kept as a separate top-level nav.

Concretely:
- Add a collapsible "Containers" section at the bottom of the collection sidebar,
  showing only the containers referenced by DataObjects in this Collection.
- Keep the top-level `/containers` nav for the power-user "manage all containers"
  view, but make it secondary (gear/admin icon, not a primary nav link).
- The DataObject "Add" flow defaults to the `default-filecontainer` but exposes
  a "Link to existing container" option one click deeper.

### 4.2 First-time upload explanation

When a user creates their first Collection and navigates to a DataObject, show
a one-time tooltip/banner:

> "Files and data you add here are stored in a secure container linked to this
> dataset. You can link the same data to multiple experiments without copying it."

This addresses P4 at the moment of first contact.

### 4.3 Container list — "linked from" breadcrumb

In the `/containers` page, each container row should show which Collections
reference it. This makes the relationship visible to the power user without
requiring graph navigation.

### 4.4 Default-filecontainer naming

Rename the auto-created container from `<CollectionName>-default-files` to
`"<CollectionName> — file store"` and add a tooltip explaining it was created
automatically and can be shared with other Collections.

### 4.5 "View in" quick link

On a Container detail page, add a "Referenced by" section listing the
DataObjects (and their Collections) that point to this Container. Click-through
navigates to the relevant Collection/DataObject.

---

## 5. The Orchestration Angle

The user identified an orthogonal use case: **orchestration** — where a
pipeline component (e.g. a digital twin, a CPACS post-processor, a climate
control loop) produces outputs that need to land in shepard without knowing
which experiment is running.

The Container-as-stable-endpoint model supports this natively:
```
Orchestration step  ──writes──►  Container (fixed endpoint)
                                      │
                     Experiment ──►└── Reference (created by researcher after the fact)
                     Digital twin ──►└── Reference (created by automation)
```

The gap: there is no "ingest trigger" — no way to notify a researcher when
new data appears in a Container they care about. This is the **N10 notification
channel** problem (aidocs/16 MTX1 / N10). Once N10 ships, a researcher can
subscribe to a Container and get notified when new data arrives.

---

## 6. The Marketing Argument

For non-technical stakeholders:

> "In most research tools, every experiment gets its own copy of the shared
> lab data (temperature sensors, vacuum levels, humidity). After ten experiments
> you have ten copies of the same sensor stream, each slightly different because
> you manually downloaded and re-uploaded it.
>
> shepard solves this with a single shared data store that every experiment
> simply references. No duplication. No drift. No 'which version is right?'
> The climate sensor writes to one place; every experiment that needs it just
> points there."

---

## 7. Phased Implementation Plan

| ID | Task | Size | Gate |
|---|---|---|---|
| CC1a | Containers section in collection sidebar (shows referenced containers only) | S | UI decision done (aidocs/85 §7) |
| CC1b | "Referenced by" section on Container detail page | XS | none |
| CC1c | Default-filecontainer rename + first-time tooltip | XS | none |
| CC1d | "Link to existing container" one-click option in DataObject add flow | S | none |
| CC1e | Container list "linked from" breadcrumb column | S | none |
| CC2 | N10 notification: subscribe to Container, notify when new data arrives | M | N10 channel design |
| CC3 | `/templates` route with customized collection-creation forms (mandatory fields, collector config) | M | UI2a templates browser |

---

## 8. Open Questions

1. **Permissions for cross-Collection container sharing**: if Collection A and
   Collection B both reference the same Container, what permissions does a
   user of B need to read the Container's data? Current answer: Read on the
   Container directly. Is that sufficient, or should there be an
   "inherit from Collection" option?
2. **Collector setup UI**: should there be a dedicated "Set up a collector"
   wizard in the admin page that creates a Container + generates a static API
   key for it? This would make the collector setup path first-class.
3. **Container ownership**: who owns a shared Container? The first Collection
   to reference it? The operator who created it? Should the admin be able to
   transfer ownership?
