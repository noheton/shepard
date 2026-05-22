---
stage: deployed
last-stage-change: 2026-05-23
---

# 00 — Doc lifecycle stages (taxonomy SSOT)

**Status.** **Live.** This is the canonical taxonomy for the
`stage:` front-matter field on every `aidocs/*.md` design doc. The
machine-generated index that groups every doc by its stage lives at
[`01-doc-stage-index.md`](01-doc-stage-index.md).

**Audience.** Anyone editing or reviewing an aidocs doc; CI; the
generator script.
**Companion rules** (from `CLAUDE.md`):
- _SSOT-per-concept_ — one canonical doc per topic.
- _Continuous doc maintenance_ — every learning + every shipped change
  updates affected docs in the same turn.
- _Consult context_ — the lifecycle stage is the cheap question to ask
  before substantive edits.

---

## 1. Why this taxonomy exists

Every aidocs doc sits somewhere on a pipeline from "raw note" to
"shipped feature." Without a uniform tag we cannot answer simple
questions like:

- "Which design docs are ready for persona review?"
- "Which features are deployed but lack regression tests?"
- "Which docs cover a v5→v6 upgrade pathway?"

A single `stage:` front-matter field fixes that. The
[`01-doc-stage-index.md`](01-doc-stage-index.md) is the rolled-up view;
this doc defines the vocabulary.

## 2. The stages

The natural progression is **1 → 8**. Most docs walk the chain in
order; some skip stages (an admin-config knob may go straight to
`feature-defined` from `fragment`). `upgrade-vX:vY` is an **overlay**
band that co-exists with any main stage. `decommissioned` is
**terminal**.

### `fragment`
Raw notes, mid-thought, captured during a session. Not yet a coherent
doc.

**Acceptance to leave:** the doc has a clear thesis paragraph and at
least one named primitive (entity, endpoint, plugin).

### `concept`
Early shape — a kernel of an idea that names a problem and a possible
direction. No mechanism yet.

**Acceptance to leave:** mechanism sketch (one paragraph) + named
companion docs / dependencies.

### `idea`
Proposal: "what if we did X?" One paragraph of mechanism + a rough
sense of where it sits in the stack. Still pre-design.

**Acceptance to leave:** a §"Design" section with sub-sections per
moving part (data model, REST surface, frontend, plugin boundary).

### `feature-defined`
Formal feature definition complete. Data model, REST shape, frontend
surface, and plugin boundary are all spelled out. Ready for
implementation review or persona audit.

**Acceptance to leave:** at least one persona / specialised-agent
review has been performed (see CLAUDE.md §"Specialized agent roles").

### `audited-by-personas`
Persona review has run against the spec — for example one or more
of the ten agent roles in CLAUDE.md (ux-auditor, data-ontologist,
api-scrutinizer, …). The agent's findings live under
[`agent-findings/`](agent-findings/). The reviewed doc carries a
companion citation to those reports.

**Acceptance to leave:** the reviewer's feedback has either been
incorporated, or explicitly closed out with a "won't fix"
justification in the doc.

### `feedback-implemented`
Persona / reviewer feedback has been incorporated. The doc reads as
the authoritative spec; the next step is code + tests.

**Acceptance to leave:** code lands AND a test plan is enumerated in
the doc (test files, coverage targets, IT fixtures).

### `tests-implemented`
Test coverage has shipped. Backend tests live in `backend/src/test/`;
frontend tests live next to components or under `frontend/test/`. The
relevant aidocs/44 row references the test files.

**Acceptance to leave:** the feature is live on a canonical fork
instance (default: `shepard.nuclide.systems`) AND the aidocs/34 row
has been updated.

### `deployed`
Live on the canonical fork instance. The `aidocs/34` ledger has the
row; the `aidocs/44` matrix marks the row `✓ shipped`; user-facing
docs in `docs/` reflect the feature.

**Acceptance to leave:** only `decommissioned` follows.

### `upgrade-vX:vY` (overlay)
Parallel band. The doc covers a specific upgrade pathway between two
named versions (e.g. `upgrade-v5:v6` for v5→v6, `upgrade-v5.2:v5.3`
for a minor bump). It co-exists with whatever main stage applies:

```yaml
stage: deployed, upgrade-v5:v6
```

Means: the upgrade design is shipped on the canonical instance AND
the doc is specifically the v5→v6 migration guide.

This is a categorical overlay, not a chronological one. A doc can be
both `feature-defined` AND `upgrade-v5:v6` if the upgrade pathway is
defined but not yet implemented.

### `decommissioned`
The feature has been retired. The doc stays in the tree for archival
reference, ideally under [`archive/`](archive/). The corresponding
`aidocs/34` row gets a `DECOMMISSIONED` annotation; if user-facing
behaviour changed, a sunset banner ships in `docs/`.

Terminal — `decommissioned` does not progress.

---

## 3. Required front-matter shape

Every aidocs doc starts with a YAML front-matter block:

```yaml
---
stage: feature-defined
last-stage-change: 2026-05-23
---
```

Rules:

- `stage:` is the **only required** field.
- `stage:` takes ONE main token from §2. The `upgrade-vX:vY` overlay
  can be added with a comma: `stage: deployed, upgrade-v5:v6`.
- `last-stage-change:` is an ISO-8601 date (`YYYY-MM-DD`). Bump it
  whenever the stage flips; **don't** bump it on body edits.
- Additional keys (`audience:`, `owner:`, `companion:`) are allowed
  but not required. The generator ignores them.

The front-matter block lives at the **top** of the file, before the
first heading. If a doc already has YAML front-matter (e.g. for some
other tooling), merge `stage:` into the existing block — do not
double-front-matter.

## 4. Querying docs by stage

### Grep one-liner

```bash
# Every deployed design doc:
grep -lE '^stage: deployed(,|$)' aidocs/**/*.md aidocs/*.md

# Every doc that covers a v5→v6 upgrade:
grep -lE 'stage:.*upgrade-v5:v6' aidocs/**/*.md aidocs/*.md
```

### The generated index

[`01-doc-stage-index.md`](01-doc-stage-index.md) groups every doc by
stage. Regenerate it after every stage flip:

```bash
python3 scripts/regenerate-doc-stage-index.py
```

Check for drift in CI:

```bash
python3 scripts/regenerate-doc-stage-index.py --check
```

Histogram only:

```bash
python3 scripts/regenerate-doc-stage-index.py --stats
```

## 5. Author-flow

When you create or edit an aidocs doc:

1. **Create:** add the `stage:` front-matter immediately. Default for
   a brand-new note is `fragment`.
2. **Promote:** when you cross a boundary (e.g. you've added the
   `Design` section — that's `idea → feature-defined`), bump the
   stage AND `last-stage-change`.
3. **Regenerate:** run
   `python3 scripts/regenerate-doc-stage-index.py` and commit the
   updated `01-doc-stage-index.md` in the same commit.
4. **Cross-reference:** if the stage flip is to `deployed`, update
   `aidocs/34-upstream-upgrade-path.md` and
   `aidocs/44-fork-vs-upstream-feature-matrix.md` per
   `CLAUDE.md §"Always: maintain the upstream upgrade path"`.

## 6. Backlog

The `DOC-STAGE` rows in
[`16-dispatcher-backlog.md`](16-dispatcher-backlog.md) track follow-on
work:

- **DOC-STAGE1** — taxonomy + index + retro-tag pass (this commit).
- **DOC-STAGE2** — pre-commit hook + Pages workflow that fails when
  an aidocs/*.md lands without `stage:`.
- **DOC-STAGE3** — per-stage filter view on the in-app `/help` site.
