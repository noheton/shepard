---
title: "Shepard predecessor history — course correction (iDMS never deployed)"
stage: decommissioned
last-stage-change: 2026-05-23
audience: [contributor, strategy]
supersededBy: aidocs/strategy/86-shepard-predecessor-systems.md
---

> **Status (2026-05-23):** Both course-corrections in this note have
> been incorporated into the final history document at
> [`aidocs/strategy/86-shepard-predecessor-systems.md`](../strategy/86-shepard-predecessor-systems.md)
> (§1 framing, §4 "prototype, used in IPRO", §5 lineage table with
> deployment-status column, §7 "designs that didn't make it beyond
> IPRO", §8 with no IDMS-MIGRATION-PLUGIN row). This file is retained
> as the provenance record for the correction.


# Predecessor history — standing correction

**User course-correction logged 2026-05-23, fkrebs@nucli.de.**

This note exists because an in-flight "Shepard predecessor history"
deliverable was being drafted in another session/agent. The user
issued a correction; this note makes the correction durable so
whoever resumes the task reads it before re-drafting §4, §5, §7, §8.

## The correction

> **iDMS never went live.**

iDMS was **designed but never deployed**. The 7 UNAS zips represent
**development artefacts of an unshipped system**, not the source code
of a deployed system.

## What this changes in the predecessor-history doc

### §1 framing
- The history is not "Shepard inherits from successful predecessors."
- The honest read may be closer to: **"Shepard succeeds where
  predecessors stalled"** — verify PRAESTO + KIBID deployment status
  independently; if they also never reached production, Shepard is
  **the first deployed system in this lineage**, which is a stronger
  positioning claim, not a weaker one.

### §4 — iDMS section
- Title shift: **"iDMS — designed but never deployed"** (or
  equivalent honest framing).
- 7 UNAS zips = development artefacts of an unshipped system.
- Authors invested substantial effort (7 components is not a sketch)
  but it never reached operational use.
- The lesson is honest: "complete on paper" ≠ "running in
  production."

### §5 — lineage chronology
- Not linear "PRAESTO → KIBID → iDMS → Shepard" of deployed systems.
- Closer to: PRAESTO (verify) → KIBID (verify) → **iDMS (designed,
  never deployed)** → Shepard (the one that landed).
- **Verify PRAESTO and KIBID deployment status before publishing**
  — don't assume.

### §7 — what got dropped
- iDMS items are **not** "features Shepard chose not to inherit."
- They are "designs that never had to face production reality."
- Be careful: don't frame iDMS as bad. The unshipped designs are
  valuable evidence of what people thought the problem was at the
  time. Over-scope may be the lesson learned for Shepard's
  narrower-scope + plugin-first model.

### §8 — adoption implications
- **No `iDMS-MIGRATION-PLUGIN` backlog row.** There are no iDMS
  users to migrate. Remove the conditional from §8.
- Stakeholders to address are **iDMS designers and adjacent
  stakeholders at DLR** who carry the institutional memory of
  "what we wanted iDMS to do."
- Shepard's adoption story for them: "the thing iDMS was trying
  to be, except this time it shipped."
- The 7 iDMS components inventory becomes a **wishlist
  confirmation** — "here's what people thought was needed; Shepard
  ships subsets; the UNAS zips are field-confirmation of demand."
- The existing backlog rows **IMPORTER-LIBRARY-SEED + OPCUA1 +
  AAS-REUSE-AUDIT** are the right shape; let the iDMS component
  inventory inform their scope without inventing a migration
  plugin.

## What stays unchanged

- PRAESTO + KIBID research (verify deployment status independently).
- Bibliography work (Welzmüller et al. eLib 215120 etc.).
- eLib search for Nuschele/PRAESTO.
- Document structure (9 sections); only the framing inside §4, §5,
  §7, §8 changes.

## Lens note

Strategy-advisor lens: "Shepard is the one that landed" is a
**stronger** positioning than "lineage of deployments" — say it
plainly if research confirms PRAESTO + KIBID also did not reach
production.

## Provenance

- Source: user message 2026-05-23, copy-pasted into this file as
  the SSOT for the correction.
- Pickup: any agent resuming the predecessor-history task reads this
  file **before** re-drafting §4, §5, §7, §8.
- When the predecessor-history doc lands, this correction note
  should be marked `decommissioned` with a pointer to the final doc.

---

## Correction layer 2 (2026-05-23): iDMS prototype status + IPRO use site

**Second user course-correction logged 2026-05-23, fkrebs@nucli.de**,
refining the layer-1 "never went live" framing.

### The refined factual position

> *"only prototype IDMS"*
> *"but used in IPRO project (research)"*

iDMS was a **prototype** — not a production system, not deployed
institute-wide. BUT it **did see use** in a specific research
context: the **IPRO project** at DLR.

The distinction matters:

- Layer 1 said: "designed but never deployed." That over-corrects.
- Layer 2 refines: "prototype that **was used** in the IPRO research
  project, but never graduated to institute-wide deployment."

These are different artefact-classes:
- "Designed only, no users" — a paper system.
- "Prototype, used in one research project" — a working system with
  bounded scope.
- "Production, deployed institute-wide" — what Shepard is becoming.

iDMS is the **middle** class. Honour that in the framing.

### Research pass — IPRO project

Web search 2026-05-23 for `IPRO project DLR Augsburg ZLP research`,
`IPRO DLR Leichtbau Produktionstechnologie iDMS`, `"IPRO" DLR
Faserverbund`, `"iDMS" DLR ZLP prototype data management`:

**No public-source hits for "IPRO" as a named DLR project.** The
ZLP Augsburg project list (DiCADeMa, Flextelligent, OIML, etc.) does
not surface an "IPRO" entry. eLib search did not turn up an IPRO
publication via the public search index.

**Conclusion:** IPRO is likely a DLR-internal research project
designation not widely publicised. **Cite it as
"IPRO (DLR research project; details unverified from public
sources)"** — same honest-reconstruction discipline as for PRAESTO
+ KIBID deployment status.

Action for the agent doing the main writing: when accessible,
check DLR eLib + the ZLP internal project list with a logged-in
session; the public web search index is insufficient.

### Concrete language to use

**§4 framing:**

> iDMS was developed as a **prototype** and saw use in the
> **IPRO project** — a DLR research project (details unverified
> from public sources at the time of writing). It did not graduate
> to institute-wide deployment. The 7 UNAS components therefore
> represent a working prototype tested in a bounded research scope,
> not a deployed production system retired in favour of Shepard.

**§5 lineage:**

> PRAESTO (deployment status: verify) → KIBID (deployment status:
> verify) → iDMS (prototype, used in IPRO research project) →
> Shepard (deployed; the first in the lineage to graduate to
> operational use across multiple use cases at ZLP).

**§7 continuity/discontinuity:**

> What iDMS demonstrated in IPRO that Shepard inherits: [requires
> domain research on what worked within IPRO scope — the 7 UNAS
> components inventory is the starting point]
>
> What didn't make it beyond IPRO: [the scaling gap — a research
> prototype serving one project doesn't necessarily survive
> institute-wide deployment requirements; over-scope may have been
> a factor; Shepard's narrower-scope + plugin-first model is the
> structural answer to that].

### Tone discipline (carry-over)

- Don't bash iDMS or IPRO. Research prototypes serving a single
  project are valuable artefacts; they're just a different
  artefact-class from deployed systems.
- Don't speculate on IPRO scope without evidence. The web pass
  returned nothing; say "unverified from public sources" rather
  than invent a project description.
- The 7 UNAS components remain valuable as **demand signal** — they
  encode what IPRO researchers thought a data management system
  needed to do. That's the inheritance, regardless of deployment
  status.

### Status

This layer-2 correction **supersedes** the layer-1 "never went
live" framing where the two conflict. The §4/§5/§7/§8 changes
described in layer 1 still apply *in shape*; the **wording** uses
the layer-2 "prototype + IPRO use site" framing.
