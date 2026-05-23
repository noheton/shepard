---
title: User research findings — 2024-06/07 interview round
stage: feature-defined
last-stage-change: 2026-05-23
audience: frontend, UX, product
anonymity: strict — no individual interviewee names, only aggregate findings
---

# User research findings — 2024-06/07 interview round

This document captures the aggregate findings from a structured user-research
round conducted between 2024-06-05 and 2024-07-18 by an external UX agency.
The round comprised 17 interviews across 5 internal user groups, with
results presented to the project team on 2024-10-23. The presentation deck
is the canonical artefact; this document is the anonymous public-repo
distillation.

**Anonymity policy:** individual interviewee identities are not recorded
here. The user-group framing below uses categories large enough to prevent
re-identification of any individual; problem-statement aggregations exclude
attributions. The source deck remains available to the project team for
internal review.

## §1 The five-phase user journey (canonical frame)

The 2024-10-23 presentation established a five-phase user-journey model
that this fork adopts as the canonical frame for organising UX work going
forward:

```
Discovery → Install instance → Create data structure → Import data → Working with the data
```

Every UX-flavoured backlog item should declare which phase(s) it serves;
every screen design should answer "what phase of the journey is the user
in?"; every documentation page should be reachable from the phase a
researcher is currently in.

This frame supersedes ad-hoc "feature areas" as the SSOT for organising
the UX backlog and frontend navigation strategy.

## §2 Methodology

- **Process:** ISO 9241-style user-centred design double-diamond (Context
  of Use → Requirements → Prototyping → Evaluation → Development).
- **This round covered:** Interviews + Problem statements + User Stories
  + Wireframes + Screen designs + Usability testing.
- **Interview scope:** 17 interviews / 6 weeks (2024-06-05 to 2024-07-18).
- **User groups:** five internal groups (FAS, PNA, MFT, PQS, plus one
  external/distributed group). Per-group identities not recorded here.
- **Per-interview output:** structured *Auswertungsbogen* (background +
  tasks + insights/observations/quotes + hurdles & challenges + what
  works well + wishes), with Key Insights distilled per column.
- **Aggregated output:** problem statements clustered by user-group
  category (researcher-developers, researchers, project leads, admin)
  and by user-journey phase.

## §3 Painpoints by user-journey phase

### §3.1 Discovery

- "What is shepard?"
- "Where is it useful? Is it useful for me?"

### §3.2 Install instance

- "What are the technical requirements to run a shepard instance?"
- "How does it scale?"
- "How can I run an instance?"

### §3.3 Create data structure

- "How can I organize my data so (future) colleagues can easily work with it?"
- "As a project lead: how can I provide a structure my team can work with well?"
- "I wish for a consistent structure across different projects/teams (maybe even locations)."
- "The wording and functionality of Shepard is unclear (Collections, Container, Data Objects?)"
- "I want to document and provide context for my data where it is easily findable and helpful."
- "I need to have a more visual overview over the data structure so I can create and navigate it easily."

### §3.4 Import data

- "It is unclear how containers work and how to upload / reference data."
- "I need help to connect machines."
- "I need help converting data from machines, or upload unconverted data from a machine."
- "I can't upload photos from my phone easily."
- "Currently, I need a workaround for big datasets and situations where I'm not connected to the internal network (like saving data locally and uploading it later)."
- "I need metainformation for my data right where it is stored."
- "Getting started is hard: programming skills necessary."

### §3.5 Working with the data

- "I can't easily find relevant projects from the past."
- "I can't find my way around my colleagues' data."
- "I don't understand the search function."
- "I need a spatial database."
- "Do I need to download the data to work with it?"
- "I can't understand the frontend."
- "Documentation is not where the data is (it is in textfiles, personal or shared onenotes, sometimes the wiki)."

## §4 Solutions on the roadmap (state as of 2024-10-23 + this fork's deltas)

The UX agency's deck named four direct responses (Tree View, Lab Journal,
Templates, FrontEnd Transition) and a broader product roadmap. This fork
has shipped or is in-flight on most of these — the cross-reference below
ties the 2024-10-23 plan to the current aidocs/16 backlog.

### §4.1 FrontEnd transition

Direction: from a technical / debugging-oriented frontend to a
user-friendly one that still helps the user understand the technical
background. Specific elements named:

- Better UX via tooltips, templates, additional labels
- At-a-glance overview for existing data structures: Tree View
- Easier creation of data structures: Tree View
- Documentation where the data is: Lab Journal
- Navigation structured along workflows

Cross-references in this fork:

- Tree View — appears in screen-design mockups in the deck; tracked as
  per-Collection sidebar + content navigation
- Lab Journal — backlog row J1d (Frontend: lab journal entry history
  panel + diff viewer, `aidocs/16` task #69)
- Templates — backlog rows: #20 (UI: surface "create from template"
  entry point) ✓ shipped; #42 (UX: "Create from template" prominent for
  basic users — predigested form path) pending; #51 (Basic mode:
  containerless UX by inferring container defaults from used templates)
  pending; #83 (TPL1+TPL2 — Shapes as templates + views, M1 milestone)
  pending
- Tooltips / additional labels — covered by ongoing UI hygiene work
- Workflow-aligned navigation — covered by the 5-phase user-journey
  frame this document establishes

### §4.2 Lab Journal requirements (deck §)

- Information and documentation is where the data is
- Connect information about data, process, experiment set-up to the
  entire project, single project steps, or machine parts
- Document set-up with photos

### §4.3 Templates requirements (deck §)

- Easy entry point into creating a new data structure for a project
- Different project teams use roughly the same structure → it is easy
  to navigate Shepard projects from other teams as well

### §4.4 Product roadmap (deck §, 2024-10-23 view)

The deck's roadmap had top-row product capabilities + a bottom row of
enabling architecture changes. This fork's status against each:

| Roadmap item (deck) | Maps to aidocs/16 / this fork's status |
|---|---|
| Tree View & Data Object Viewer | shipped in frontend (Collection sidebar + per-DO viewer) |
| Lab Journal | task #69 (FE history panel + diff viewer) pending; backend in fork (J1) |
| Create Project (w Templates) | task #20 shipped (entry point); template substrate evolving (TPL1+TPL2, M1 milestone) |
| Frontend Cutover | shipped — the noheton fork is a frontend rewrite |
| Easy File Upload | shipped #135 (file-upload progress); ongoing improvements |
| Advanced Search | partial (Collection search shipped, unified search pending #111) |
| Data Parsers | tracked under importer plugin family + plugin SPI (47) |
| Basic Search | shipped (Collection-scoped search) |
| Simple Device Connex | tracked under home-showcase + future plugin paths (#19, OPC-UA plugin idea) |
| Explorative Learning | partial (MCP shipped #30, JupyterHub plugin pending #60) |
| Versioning | shipped (PV1a/PV1b, task #66) |
| Rework Database Architecture | in-flight (SHACL changeover, tasks #120, #127) |
| Spatial DB | shipped (PostGIS + shepard-plugin-spatial); UI viewer pending (task #79) |
| ODIX integration | partial (ODIX milestone reports + AAS plugin path) |
| Extend Authentication | shipped (A0 instance-admin) |
| Facilitate Deployment | shipped (compose stack + Makefile redeploy targets) |
| GAIA-X Federated Shepard | designed (Federated Shepard sketches 2026-05-23, federation strategy doc); implementation pending |

### §4.5 Working method (2020–2024 + ongoing)

The deck's timeline (slide 39) anchored the project at 2020-05-29
KickOff with intermediate milestones (Monorepo, Quarkus Migration,
Breaking Change release, First frontend view at S7 = 2024-10-23). This
fork's lineage continues that timeline; the predecessor history at
`aidocs/strategy/86` covers the pre-2020 origins.

## §5 Implications for this fork

### §5.1 Researcher-developer / researcher / project-lead / admin split

The deck's problem-statement clustering identified four distinct user
roles, each with different painpoint emphases. Worth applying as a UX
audit lens:

- **Researcher-programmers** (resource-rich, code-confident): want
  programmable APIs, MCP, scriptable bulk operations.
- **Researchers** (domain-focused, varying programming comfort): want
  visual overview, copyable templates, low-friction upload.
- **Project leads** (coordinative): want structure consistency across
  team / projects / locations, audit trail, contributor visibility.
- **Admins**: want resource overview, retention controls, permission
  audit.

Cross-reference: the **basic mode / advanced mode** split
(`aidocs/frontend/82-basic-vs-advanced-mode-matrix.md`) maps roughly to
researchers (basic) vs researcher-programmers (advanced); the project-
lead and admin roles need their own UX flows that this fork hasn't yet
fully named.

### §5.2 The container-vocabulary confusion is a known fix

"The wording and functionality of Shepard is unclear (Collections,
Container, Data Objects?)" was named in the 2024 interviews and remains
a vocabulary tax this fork's docs (CLAUDE.md, README, the in-app help)
are working to reduce. The 5-phase user-journey frame helps — instead
of asking the user to learn the Collections/DataObjects/Containers
vocabulary up-front, the docs can route them by phase ("you're
importing data; here's the relevant terms").

### §5.3 The "spatial database" wish is concrete

Multiple painpoints surfaced the need for spatial-coordinate-based data
discovery. This fork has answered with PostGIS + shepard-plugin-spatial
(backend shipped) and the spatial-query workflow specified in the
`UserStoryShepardUltraschall` user-story PDF (Mayer 2024-11-04, see
that artefact). The frontend viewer for spatial data remains task #79;
the Trace3D view (task #142) extends the spatial+timeseries pattern.

### §5.4 "Documentation lives elsewhere" is a tractable problem

The painpoint "documentation is in textfiles / OneNote / wiki" maps
directly to the Lab Journal work (J1 backend shipped; task #69 frontend
pending). The fork's labjournal-as-scientific-twitter framing
(memory: 2026-05-23 user idea) extends this in the right direction.

## §6 Maintenance

This document is the durable record of the 2024-10-23 UX research round.
Future user research rounds should:

1. Be captured in sibling docs under `aidocs/frontend/0X-user-research-findings-YYYY.md`
2. Preserve anonymity per §0
3. Use the same five-phase user-journey frame for organising findings
   (so cross-round comparisons are tractable)
4. Tie each finding to an aidocs/16 row + the relevant frontend / backend module

Updates to THIS document should be limited to:

- Cross-reference updates as backlog rows ship or move
- Methodology refinements that came from later rounds
- Stage transitions

Do not add interviewee names retroactively.
