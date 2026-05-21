# Reluctant Senior Researcher — Persona Findings

**Persona**: Senior aerospace researcher, DLR, 28 years tenure.
**Setup**: 40 TB NFS, 600-row Excel master sheet, zero RDM system has outperformed my own.
**Task**: Evaluate Shepard honestly and report every moment I'd walk away.

---

## What I found

I spent a session reading the code and the documentation as a first-time evaluator. I looked at the pages I'd actually land on, the annotation workflow, the timeseries container, the provenance graph, the docs, and the export story. I compared what I found to what Kadi4Mat offers (the closest European competitor I'm aware of). Below is my honest field report.

---

## "This is worse than my current system" — the conversion killers

**1. I cannot export timeseries channels to CSV.**

The timeseries container page (`/containers/timeseries/[id]`) has a CSV _upload_ button and nothing else. The RO-Crate download is at collection level only, produces a ZIP, and I'd have to unpack it and figure out the internal structure. My current workflow: `rsync NFS:/data/TR-004/ ./ && python parse_channels.py`. Three seconds. Shepard offers me... a ZIP with an internal format I don't know yet. For 28 years I've been able to `cp` my data. This is a hard stop for day-to-day analysis work.

**2. Annotating my data requires speaking ontology.**

I have a standard set of attributes I put on every test run: `bench = B1`, `propellant = LOX/LH2`, `test_engineer = Müller`, `is_fired = true`. In my Excel sheet these are column headers. In Shepard, the "Attributes" panel accepts plain key-value pairs — fine. But the "Semantic Annotations" panel (which is the searchable, cross-collection metadata layer) requires me to pick a **property IRI** and a **value IRI** from a SPARQL-backed ontology repository. The search dialog says "Type at least 2 characters to search ontology terms." I type `LOX` and get nothing. I type `propellant` and get nothing unless the instance admin has loaded a propellant ontology. So the searchable metadata layer requires ontology setup I cannot do myself. My Excel column headers work right now without an admin.

Kadi4Mat's "extra metadata" on Records accepts typed key-value pairs with no ontology requirement — the structure is user-determined, not enforced by an admin-loaded vocabulary. A researcher can add `propellant: "LOX/LH2"` in 30 seconds. Shepard's Attributes panel is technically comparable, but Attributes are not the cross-collection discovery layer — Annotations are. That gap matters for adoption.

**3. The provenance graph and the lineage graph are slow and unnavigable at scale.**

`CollectionLineageGraph.vue` loads every DataObject in the collection via an exhaust-all-pages while-true loop before rendering — 200 per API call, unbounded pages, everything in browser memory. For a collection with 1,000 DataObjects this will hang the browser. Labels are truncated at 18 characters. There is no click-to-navigate: I cannot click a node and go to that DataObject. The graph is eye-candy for a 15-run demo, not a tool I'd use with three years of campaign data.

My folder tree gives me `ls -lt | head -20` to find the latest run in 0.2 seconds and `cd TR-004` to navigate it. The Shepard graph gives me a static picture with truncated labels that I can't interact with.

**4. The sidebar hides Containers in basic mode.**

`CollectionSidebar.vue` wraps the entire Containers section in `<template v-if="advancedMode">`. In basic mode (the default for new users), I cannot see or navigate to the timeseries containers my data is in. This violates the system's own design policy ("advanced is a strict superset of basic") and means the first time I try to find my sensor data I'll hit a dead end. There is no indication in basic mode that this content exists or how to find it.

**5. The user-facing docs don't answer my questions.**

`docs/user-guide.md` explains what a Collection is. It does not answer:
- "How do I find the data from the June 2 test run?" (no search-by-date, no search-by-attribute in the docs)
- "Can I export the channels I care about to Excel?" (answer: no, but the docs don't say that)
- "Who has access to TR-004?" (the permissions model is described abstractly; no "check access" button is documented)

The help catalogue is comprehensive — 47 pages across 5 sections — but the task-oriented front door ("I want to do X, here's how") is thin.

**6. What happens when Shepard is down?**

My NFS drive has never gone down in 28 years. It has no authentication layer, no container orchestration, no database that needs migrating, no Keycloak session to expire. Shepard is a multi-service stack: Neo4j, TimescaleDB, MongoDB/GridFS, PostgreSQL, MinIO, Keycloak, the Java backend, the Nuxt frontend, and a reverse proxy. Any of those can go down. When they do — during a hot-fire run, during a data download before a meeting — I cannot get to my data via the normal interface. The raw files in MinIO are reachable directly if I know the bucket path and have a MinIO client, but that is not documented as a fallback procedure anywhere I could find. The uncertainty alone is a reason to keep the NFS running in parallel indefinitely. Nobody has told me what the disaster recovery SLA is.

**7. The 5-tuple channel identity will break my analysis scripts.**

Every timeseries channel requires five fields: measurement, device, location, symbolic name, field. The UI displays all five as separate columns. Any script I write to pull channel data has to carry five parameters per channel. My current naming convention is `tr004_pc_chamber.csv` — one string, one file. Shepard's model is richer and more correct, but the friction cost is real. The upcoming appId migration (single opaque identifier) will fix this, but it's not shipped yet.

---

## The one killer demo moment

Here is the moment I'd stop shrugging.

I open TR-004 in Shepard. I see that the vibration channel (`vib_fuel_pump`) already has a red band across t=7.8–9.2s with the label "turbopump vibration anomaly — MAD spike, 4.8σ — AI generated." The system found this automatically, without me writing a detection script. I click the annotation and see: severity, confidence, the activity that created it (the AI1b rolling-median scan), and the linked investigation DataObject.

Then I click "Create snapshot" and get a DOI-resolvable, immutable, timestamped record of this exact state of the campaign — every channel value, every annotation, every predecessor link — in one button click.

These two things my NFS + Excel setup provably cannot do. Anomaly detection that runs automatically across all my channels and creates a searchable, shareable, citable record is a genuine capability gap. I would sit down for an hour to understand the system after seeing that.

The prerequisite: the demo must actually show this. The LUMEN seed script does call the anomaly detector (`best_effort_anomaly_detection()`) at the end of the seed run — but gracefully skips the call if the AI1b endpoint returns HTTP 404 or 501, meaning the annotations are silently absent when the feature is not deployed. If I sit down with a Shepard instance where AI1b is not active and the anomaly flags aren't on TR-004, the demo fails before it starts. The killer moment only works on a fully-deployed instance.

---

## Minimum feature set before I migrate anything real

In priority order:

1. **CSV / Parquet channel export.** A "Download channels" button on the timeseries container page that produces a CSV or Parquet file I can open in Python or MATLAB. Without this, my analysis workflow is broken. This is not negotiable.

2. **Plain-text attribute search.** I need to search `bench = B1 AND date > 2024-01-01` across all DataObjects in a collection without knowing any ontology. Kadi4Mat does this with their typed "extra metadata" fields. Shepard's Attributes are already plain key-value; the search just needs to expose them as filters.

3. **Containers visible in basic mode.** Remove the `v-if="advancedMode"` gate from the Containers section of the sidebar. A new user must be able to find their sensor data without knowing they need to toggle a hidden mode.

4. **Import my existing data without touching the folder structure.** I need a bulk importer that walks a directory tree, creates one DataObject per folder, and attaches the files as references — using the existing folder names as DataObject names. If I have to manually create 600 DataObjects through the UI, adoption is zero. The importer plugin design exists in aidocs; it needs to ship.

5. **"Who can see this?" visible on each DataObject.** A one-line access summary ("Visible to: DLR-ZLP, viewer-group-3") on the DataObject page. I need to know this before I tell a colleague "yes, you can access TR-004."

6. **Snapshot-pinned export in docs.** Document clearly that the RO-Crate ZIP from a snapshot is immutable and can be cited. Right now the docs describe snapshots and exports in separate sections. The connection between them — "this is how you get a citable frozen record" — is not made explicit.

---

## Honest verdict

I would not migrate real data in the next six months. The CSV export blocker alone stops me — I cannot break my analysis pipeline for an experiment that's still running. But I would not dismiss the system either.

What changes my mind: the anomaly detection and snapshot story is genuinely compelling. No folder-plus-Excel setup can do what AI1b + V2 snapshots do. If the team ships a CSV export button and a bulk directory importer before my next campaign starts, I run a parallel pilot with a subset of the data. If the pilot shows the anomaly detection working on my real channels with zero extra annotation effort on my part, I migrate the campaign and keep the NFS as a backup read-only mirror.

My adoption condition is not "Shepard must be better than my folder structure in every way." It's "Shepard must do one thing I genuinely cannot do myself, with low enough migration friction that the pilot doesn't cost more than the return." The anomaly detection passes the first test. The migration friction — no CSV export, no directory importer, 5-tuple identity, ontology annotation requirement — currently fails the second.

---

## Recommendations, ordered by "fix this first"

**1. Ship a CSV/Parquet export button on the timeseries container page.**
One button. Downloads all channels for a time window as a CSV or Parquet file. This is the single highest-impact feature for researcher adoption. Without it, Shepard cannot be a primary tool for anyone who does numerical analysis. Estimated effort: one backend endpoint (`GET /v2/timeseries/{appId}/export?format=csv&from=&to=`) + one frontend button. No new architecture needed.

**2. Remove the `v-if="advancedMode"` gate from sidebar Containers.**
One-line frontend fix. Containers must be visible in basic mode — a new user arriving at a collection must be able to navigate to their sensor data without knowing a hidden mode exists. This actively contradicts the system's own "advanced = strict superset of basic" policy and should be treated as a bug, not a feature decision.

**3. Ship the directory bulk importer.**
The importer plugin is designed but not shipped. Without it, the cost of migrating existing data is prohibitive. The minimum viable version: give the user a manifest generator that walks a directory tree and produces the JSON import plan, then let the existing importer execute it. Even a CLI tool (not UI) is enough for the pilot phase.

**4. Add plain-text attribute filters to the DataObjects panel.**
Allow filtering by `key = value` on the Attributes dict. This is how Kadi4Mat's extra metadata search works and it's what every researcher reaching for their Excel filter column expects. The Attributes are already stored; it's a query plumbing problem.

**5. Make the anomaly detection demo resilient on partial-feature deployments.**
The seed script does call `best_effort_anomaly_detection()` but silently skips if the AI1b endpoint returns HTTP 404 or 501. The result is that a demo Shepard instance without AI1b active shows TR-004 with no anomaly annotations — the most compelling first impression is missing. Either gate the demo narrative on AI1b being present, or pre-seed the TR-004 annotations as static data so the graph always shows the red band regardless of which backend features are deployed.

**6. Write a "What Shepard does for 40 TB on NFS" migration guide.**
One page, honest, covering: parallel-run strategy, bulk import options, what stays on NFS (raw files, analysis scratch), what goes into Shepard (annotated outputs, campaign structure, anomaly records). The current docs assume the reader is starting fresh. Most researchers at DLR are not.

---

## Adoption likelihood score

**4 / 10** today. Would rise to **7 / 10** the day CSV export and the bulk importer ship.

The 4 points are real: the anomaly detection + snapshot story is a genuine differentiator, the collaborative ACL model solves a real pain point (I currently email ZIPs to collaborators), and the semantic annotation infrastructure — once the ontologies are loaded — is something no folder structure can replicate. The remaining 6 points require the blockers above to close.

---

*Codebase snapshot: main branch, 2026-05-21.*
*Kadi4Mat comparison grounded in: Brandt et al. (2021), "Kadi4Mat: A Research Data Infrastructure for Materials Science", Data Science Journal; Kadi4Mat readthedocs v1.7–1.8; NHR4CES 2025 workshop slides.*
