---
stage: feature-defined
last-stage-change: 2026-06-13
---

# Feature Verification & Dual-Doc Standard

**Scope.** The contract every Shepard feature — core or plugin —
satisfies to count as *verified and documented*. Operator request
(2026-06-13): *"Check everything core and plugins for UI placeholders
and verify functionality. Verification is done through Playwright
end-to-end and documented in the user manual (including screenshots).
Every feature documented in the User Doc (basic mode) so a researcher
with few IT skills can understand; separate Power User Doc for in-depth
inner workings and API usage (advanced mode)."*

This doc is the SSOT for the four-row work program the hourly
dispatcher and ad-hoc agents execute. The per-feature matrix that
drives it is
`aidocs/agent-findings/ui-feature-inventory-2026-06-13.md`. The
journeys/personas the docs are written against live in
`aidocs/agent-findings/ux-journeys-personas-dualdoc-2026-06-13.md`.

## The two tracks (already structural in the repo)

The repo already ships a two-track docs system; this standard maps the
operator's "basic vs power-user" split onto it, aligned to the
[Diátaxis](https://diataxis.fr) framework:

| Track | Path | Audience | Mode | Diátaxis quadrant |
|---|---|---|---|---|
| **Basic** | `docs/help/<feature>.md` | Researcher with few IT skills | Basic | Tutorial + How-to |
| **Advanced** | `docs/reference/<feature>.md` | Power user / developer | Advanced | Reference + Explanation |

Both are served as the public Pages site **and** the in-app `/help`
route (`aidocs/ops/49-in-app-user-docs.md`). Plugin docs stay in the
plugin module (`plugins/<id>/docs/`, per CLAUDE.md) and are
cross-referenced from `docs/reference/plugins.md`.

The frontend basic/advanced toggle
(`frontend/composables/context/useAdvancedMode.ts`) is the in-app
mirror: advanced is a strict **superset** of basic — the basic doc
never references a control the advanced-only user cannot also see.

## A feature is "done" when all four hold

For every user-visible feature (a page, a reference kind, a container
kind, a tool, an admin surface, a plugin payload kind):

1. **Real, not placeholder.** No `frontend/components/common/placeholder/`
   stub stands in for shipped backend functionality. A placeholder is
   acceptable only as an explicitly-tracked `alpha` interim with a
   `PLACEHOLDER-<feature>` backlog row.
2. **Playwright-verified end-to-end.** A spec under `e2e/` exercises
   the feature's canonical journey (entry → action → success state)
   against a live instance, at **both 1920** (most common user res)
   **and 4K** (per `feedback_validate_user_viewport.md`). The spec
   captures screenshots into `docs/help/img/<feature>-*.png` and (for
   advanced detail) `docs/reference/img/<feature>-*.png`. A feature is
   never marked verified without a green Playwright run.
3. **Basic-mode doc.** `docs/help/<feature>.md` — task-oriented, plain
   language, no jargon, screenshot-led; answers "how do I do X" for a
   researcher who is not an IT specialist. Follows the basic template
   below.
4. **Advanced-mode doc.** `docs/reference/<feature>.md` — entity model,
   every endpoint with a worked request/response, config keys, the
   inner-workings explanation a power user needs at 2 AM. Follows the
   advanced template below.

## The four backlog row types

Filed in `aidocs/16` under the `### UIVERIFY` section, one set per
feature, IDs the dispatcher matches:

- `PLACEHOLDER-<feature>` — replace a placeholder stub with real
  functional UI **+ Vitest**. Ships via PR. Size per scope.
- `UIVERIFY-<feature>` — add the Playwright e2e (1920 + 4K) + commit
  the screenshots. Direct-to-main (test + image artefacts).
- `DOC-BASIC-<feature>` — write `docs/help/<feature>.md`. **Depends on**
  the `UIVERIFY-<feature>` screenshots. Direct-to-main.
- `DOC-ADV-<feature>` — write `docs/reference/<feature>.md`.
  Direct-to-main.

Pick the smallest dispatchable row first (XS/S). A `DOC-*` row is not
dispatchable until its `UIVERIFY-*` screenshots exist.

## Basic-mode page template (`docs/help/<feature>.md`)

```markdown
---
title: <Feature> — how to <task>
---
# <Feature>: <task in plain words>

**What this is for.** One sentence a non-IT researcher understands.

**Before you start.** What you need (e.g. "a Collection with at least
one DataObject"). No installation/CLI knowledge assumed.

## Steps
1. From <top-nav entry> click … ![step](img/<feature>-step1.png)
2. …
3. You're done when … ![result](img/<feature>-done.png)

## If something looks wrong
- <common confusion> → <plain fix>

> Power user? See the [reference page](../reference/<feature>.md) for the
> data model and API.
```

## Advanced-mode page template (`docs/reference/<feature>.md`)

```markdown
---
title: <Feature> — reference
---
# <Feature> (reference)

**Concept.** What it is in the data model; which entity/kind, which
`appId`, how it relates to Collections/DataObjects/References.

## Data model
- Neo4j entity / payload kind / config singleton, key fields.

## API
| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/...` | … |

### Worked example
\`\`\`bash
curl -H "X-API-KEY: $KEY" .../v2/...
\`\`\`
(request + response shown)

## Inner workings
- Resolution path, renderer/transform SPI, provenance Activity emitted.

> New here? Start with the [task page](../help/<feature>.md).
```

## Cross-references

- `aidocs/ops/49-in-app-user-docs.md` — in-app `/help` route + the
  Playwright-screenshot-into-repo pipeline (§2.2 docs catalogue).
- `CLAUDE.md` "keep user-facing docs in step" + "ship a UI stub" +
  "top-nav reachable before beta" + "every reference type ships
  complete create+edit+delete UI" — this standard is the verification
  arm of those rules.
- `feedback_three_audience_docs.md`, `feedback_validate_user_viewport.md`,
  `feedback_validate_via_ui.md`, `feedback_ui_stub_required_for_deploy.md`.
