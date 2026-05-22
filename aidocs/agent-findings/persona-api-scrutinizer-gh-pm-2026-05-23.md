# Persona — API Scrutinizer (Minimalist) — GH-PM adoption review

**Persona.** API Scrutinizer (Minimalist) per CLAUDE.md §3.
**Lens.** Redundancy, inconsistency, leaky abstractions, wire-shape
rigour, machine-readable contracts. *"The best API is the smallest
API that solves the problem."*
**Date.** 2026-05-23.
**Source reviewed.** `aidocs/strategy/85-github-project-management-policies.md`,
`scripts/trace-feature.sh`, `aidocs/platform/106-requirements-traceability.md`,
`.github/labels.yml`, real `git log` history.

---

## TL;DR — verdict on the six questions

| Q | Vote | One-line rationale (persona) |
|---|---|---|
| Q1 — adopt policy as-is | **Yes, with edits** | Contract is sound; ~12 "should"s want "MUST". |
| Q2 — `v6.0.0-rc.1` scope | **Yes** | A milestone with cited row IDs IS the wire contract. |
| Q3 — backfill via dry-run | **Yes** | The trace-feature surface IS the dry-run output. |
| Q4 — auto-file Issues for in-flight agents | **No** | The 4-gate filter is the API; auto-file leaks state. |
| Q5 — Projects board manual | **Yes — manual** | GraphQL field IDs drift; the board is read-only UI. |
| Q6 — PR-scope hook | **Trust now, hook later** | Add the CI lint when traceability index goes live. |

---

## Q1 — adopt the policy as-is?

**Finding.** The policy is **wire-shape coherent**: aidocs/16 → branch
→ PR title → commit scope → release notes form a typed pipeline. The
nine-surface trace (§14) is the inverse function. That's the smallest
API that solves the traceability problem.

**The leak.** §7 says "every commit message follows Conventional
Commits with scope = the aidocs/16 row ID" — but doesn't define the
scope grammar. The trace-feature.sh regex `[A-Za-z0-9+-]*\b${ID}\b[A-Za-z0-9+-]*`
is the *implicit* grammar. **Promote the regex to the policy doc** —
that's the machine-readable contract.

**Recommendation.** Adopt with three edits:
1. **Pin the scope grammar in §7** as `[A-Z][A-Za-z0-9-]*` with the
   `+` separator for combined scopes. Make case-sensitivity explicit
   ("IDs are uppercase as authored; the matcher is case-sensitive").
2. **§14 query nine surfaces:** add surface #10 — the
   `aidocs/44-fork-vs-upstream-feature-matrix.md` row (it's already
   in the "companion docs" list at §16 but missing from the trace
   script).
3. **List the SBOM-attachment requirement (§5.6) as a MUST** — it's
   the only release-artefact that's machine-consumable for downstream
   compliance scans.

**Cost of being wrong.** If the scope grammar drifts (mixed-case,
exotic chars), the §14 trace becomes lossy — release notes show
"uncategorised" entries, and `aidocs/34` rows orphan. The policy
already warns about this in §15 anti-pattern #4 but doesn't make the
check executable.

---

## Q2 — `v6.0.0-rc.1` scope as listed

**Finding.** A milestone description that cites aidocs/16 row IDs IS
a machine-readable contract — `gh api repos/.../milestones/N` returns
them; release-notes auto-render groups commits by scope; the bundle
either matches or it doesn't.

**Recommendation.** Yes. The proposed scope (post-MFFD-import +
Garage + v15.2 + WAAPI + Bibliography + Origin myth + DOC-STAGE +
GH-PM + v1-compat Phase 1) is **coherent in audience**: every
bundled item is admin-visible or developer-visible — no internal
refactor noise. That's the right shape for an RC.

**Edit.** Cite the row IDs explicitly in the milestone body (per §4):
`FS1d`, `IMPORT-W1`, `WAAPI-1`, `BIB-1`, `ORIGIN-1`, `DOC-STAGE-1`,
`GH-PM1..GH-PM5`. If a row ID doesn't exist for one of the bundles
(e.g. "v1-compat Phase 1"), introduce it in the same PR — per §6,
"there is no PR without a row."

**Cost of being wrong.** A milestone whose contents don't match its
description is the worst possible release-note outcome. The auto-
render workflow doesn't validate this — it just groups by scope. So a
human ships an RC, and aidocs/34 is missing two rows that landed
inside it. Mitigation: pre-flight checklist item #1 already covers
this (§5).

---

## Q3 — backfill via dry-run

**Finding.** `scripts/trace-feature.sh` IS the dry-run. Running it
against every shipped aidocs/16 row whose Status is `done` reveals
exactly which of the nine surfaces are missing. Empirically: I just
ran `./scripts/trace-feature.sh FS1b` — 0.28s, all nine surfaces
populated. That's the contract working.

**Recommendation.** Yes. The "dry-run plan first" is the right
sequencing because it surfaces orphan commits and missing aidocs/34
rows **before** they get baked into a release. Wire it as a script:

```bash
# scripts/audit-traceability.sh — runs trace-feature for every
# row in aidocs/16 whose Status is `done` or `shipped`, reports
# any surface that returned empty.
```

**Cost of being wrong.** Without the dry-run, the first release-
notes auto-render reveals the orphans — and at that point the
release tag is already cut. A pre-flight script is cheap insurance.

---

## Q4 — auto-file Issues for in-flight agents?

**Finding.** §3 gate #4 says "agent dispatch → optional Issue at
dispatch." Optional. Making it automatic would invert the SSOT
direction (Issue becomes the leader; aidocs/16 follows) and break
the one-way sync rule (§3).

**Recommendation.** No. The agent worktree commit IS the durable
record (per memory rule `feedback_agent_worktree_must_commit.md`).
The aidocs/16 row IS the public ledger. An auto-filed Issue is
duplicated state with a worse query surface (`gh issue list` vs
`grep aidocs/16`).

**Edit to the policy.** §3 gate #4 currently says "optional Issue
opens at dispatch." Tighten to: "Issue opens at dispatch **only**
when the agent's work is externally-discoverable (e.g. it'll close
an aidocs/16 row that external contributors are watching). For
internal agent dispatch, no Issue."

**Cost of being wrong.** Auto-filed Issues become noise. The board
gets 50 cards for in-flight worktree branches that close in <24h.
External contributors see in-flight work and assume it's claimed.
The 5+ external contributors / month gate (GH-INFRA4) never trips
because the signal is buried in agent churn.

---

## Q5 — Projects v2 board: manual vs scripted

**Finding.** Projects v2 GraphQL has **two field-naming layers**:
the human-visible field name (`Status`, `Stage`, `Severity`) and the
GraphQL field ID (`PVTF_lADOABCxxxxxxxx`). The IDs change if a field
is deleted+recreated. Scripted setup hardcodes IDs that drift the
first time someone edits the board in the UI.

**Recommendation.** Manual UI setup per `docs/ops/github-projects-
board-setup.md`. The board is **read-only output**: cards arrive via
auto-add-to-project (rule-driven, not script-driven); statuses sync
via labels. A human sets up the columns + saved views **once**;
nothing else writes to the board.

**Edit.** Add to §9: "The board MUST NOT be a write target for any
automation. All board mutations flow from label/state changes on the
underlying Issue. If a script needs to write to a board field
directly, that's a sign the field belongs on the Issue (as a label
or in the body), not on the board."

**Cost of being wrong.** A scripted `gh project field-create` setup
that runs in CI works exactly once, then drifts. Operators debug
"why did Sunday's CI run reset all custom fields?" The board becomes
unreliable; people stop using it; aidocs/16 becomes the only source
of truth (which is fine — but then why have the board?).

---

## Q6 — PR-scope hook: install now or trust?

**Finding.** A pre-receive hook that rejects PRs without a valid
Conv-Commits scope is **the wrong layer** for early enforcement. The
hook fires before the contributor sees the policy. CI lint that
posts a comment ("your commit scope `feat(fs1b)` doesn't match any
aidocs/16 row — did you mean `FS1b`?") is the friendly version.

**Recommendation.** Trust now. Install the lint when Option A
(`scripts/build-traceability-index.py` per `aidocs/platform/106 §3`)
ships — that script already validates scopes against aidocs/16, so
it's a free check.

**Edit.** §7 currently says "case is significant. The IDs are
uppercase as authored." Add: "When the traceability index CI lint
ships (per aidocs/platform/106 Option A), a PR with a non-matching
scope receives an automated review comment. Until then, trust +
review-time fix-up."

**Cost of being wrong.** A pre-receive hook installed now blocks
legitimate PRs while contributors learn the convention. A trust-
period gives time for the lint to ship + tested against history
(573 IDs in aidocs/16; ~10 use combined-scope syntax; one IDs has
a slash — see §"Specific scrutiny" below).

---

## Specific scrutiny — the items called out in the prompt

### S1. The PR-title Conv-Commits scope as a machine-readable contract

**The regex in `trace-feature.sh` is:**
```
^[a-z]\+([A-Za-z0-9+-]*\b${ID}\b[A-Za-z0-9+-]*): 
```

**Empirical test (against `git log --all`):**

| Test | Real commit | Pass? |
|---|---|---|
| Plain scope `feat(FS1b):` | `ef82feb3 feat(FS1b+FS1d): ...` | ✓ |
| Combined scope `feat(FS1b+FS1d):` | same | ✓ — handled |
| Lowercase `feat(fs1b):` | `./trace-feature.sh fs1b` → 0 hits | **✗ — silently misses** |
| Lowercase free-text scope `docs(backlog):` | exists historically | works (different ID) |
| ID with slash `T1a-aidocs/54` | hypothetical scope `feat(T1a-aidocs/54):` | **✗ — `/` not in `[A-Za-z0-9+-]`** |
| ID with `#` like `fix(#148):` | none in aidocs/16 | n/a, but doc §1 example would fail |

**Findings:**

1. **Case sensitivity is a contract** — but it's an *implicit* one.
   §7 says "case is significant" in prose but the matcher silently
   misses on case-skew. A CI lint that *also* greps case-insensitive
   and warns on mismatch would catch the 90% of typo cases.

2. **Combined scopes (`FS1b+FS1d`) work** — verified empirically.
   Good. But the policy doesn't *recommend* combined scopes; §15
   anti-pattern #4 implies one-scope-per-commit is the norm.
   **Resolve the ambiguity in §7:** "Combined scopes (`scope1+scope2`)
   are permitted for cross-cutting commits but discouraged. Prefer
   splitting the work into per-scope commits when feasible."

3. **IDs with `/` break the matcher.** Real example: aidocs/16 has
   `T1a-aidocs/54`, `T1c-aidocs/54`, `T1d-aidocs/54`, `T1f-aidocs/54`.
   If anyone uses `feat(T1a-aidocs/54):` as a scope, the matcher
   misses it (slash not in `[A-Za-z0-9+-]`). **Edit aidocs/16 to
   normalise these IDs** to `T1a-A54`, `T1c-A54`, etc. — strip the
   path separator from the ID itself.

4. **The doc §1 example `fix(#148):` is inconsistent.** `#N` is a
   GitHub-Issue reference, not an aidocs/16 row ID. §6 implies the
   scope IS the row ID. Drop the `fix(#148):` example or clarify
   that `#N` is reserved for hot-fix references to closed Issues
   that lack an aidocs/16 row.

### S2. The label axes — orthogonal or overlapping?

**Six axes per §10.** Let me check for overlap:

| Axis pair | Risk | Verdict |
|---|---|---|
| `severity:` × `status:` | Independent | ✓ orthogonal |
| `area:` × `type:` | Independent | ✓ orthogonal |
| **`status:` × `stage:`** | `status:done` AND `stage:deployed`? | **✗ overlapping** |
| **`status:` × `stage:`** | `status:in-progress` covers `stage:{feedback-implemented, tests-implemented}` | **✗ overlapping** |
| `effort:` × everything | Independent | ✓ orthogonal |
| `type:breaking` × `severity:critical` | Both can mean "scary" | mostly orthogonal — `breaking` is admin-visibility, `critical` is impact |

**Finding.** **`status:` and `stage:` overlap.** §2's mapping table
explicitly maps stages to statuses:
- `stage:feedback-implemented` → `status:in-progress`
- `stage:tests-implemented` → `status:in-progress` (different stage,
  same status)
- `stage:deployed` → `status:done`

So the two axes carry redundant information. The Projects v2 column
is derived from `status:`, but `stage:` is the higher-resolution
view. **One of them should be a derived field, not a manually-
applied label.**

**Recommendation.** Make `status:` derived from `stage:`. Drop the
`status:*` labels; let a Projects v2 automation rule derive the
column from `stage:*`. Reduces the manual-label burden from 6 axes
to 5; eliminates a class of state-machine-drift bugs (§15 anti-
pattern #5).

**Cost of being wrong.** Without the change, every Issue triager
has to apply two labels that mean correlated things. They'll
diverge. The next reviewer asks "which one is authoritative?" — and
the policy doesn't say.

### S3. The §14 nine-surface query — does it resolve in <1min on a real ID?

**Empirical.** Just ran `./scripts/trace-feature.sh FS1b`:
- Total: **0.28s user / 0.06s system / 0.80s wall** on a populated
  repo (4 commits, ~25 design-doc hits, full `git log --grep`).
- All nine surfaces returned data.
- Output ~80 lines of tabular + free-text.

**Verdict.** Comfortably under 1 minute. The script is well under the
SLA.

**What's missing from the nine surfaces:**
1. **`aidocs/44` row** — listed as a companion doc (§16) but **not
   queried by trace-feature.sh**. Should be surface #10.
2. **Open PRs** referencing the ID — `gh pr list --search "ID
   in:title"` is one line; pairs with surface #8 (Issues).
3. **Plugin docs** — `plugins/*/docs/*.md` that name the ID. Most
   plugins shipping today (FS1b, KIP1c, etc.) have plugin self-docs
   per CLAUDE.md "Always: plugins ship their own documentation."
4. **`docs/reference/*.md`** that name the ID — the user-facing docs
   referenced by CLAUDE.md "Always: keep user-facing docs in step."

**Recommendation.** Add surfaces 10–13 to the script in one PR. The
script is read-only + cheap; adding four more greps doesn't push it
over the 1-second budget.

---

## Contracts to tighten — "should" → MUST (machine-checkable)

Every place the policy says "should/expected/recommended" where the
underlying mechanic admits machine enforcement:

| § | Current text | Tighten to | Why machine-checkable? |
|---|---|---|---|
| §2 | "Mapping table" (descriptive) | "**MUST** match — CI fails on mismatch" | `.github/labels.yml` + `aidocs/00-doc-stages.md` regex-diff |
| §3 gate #4 | "optional Issue" | "Issue **MUST NOT** be auto-filed for purely-internal agent work" | `gh issue list --author <bot>` check |
| §4 | "A row may belong to at most one milestone at a time" | "CI **MUST** reject milestone moves until the source milestone is closed" | `gh api` query |
| §5 #4 | "runs clean" (build-traceability-index) | "Release pipeline **MUST** abort on zero orphan commits" | exit code |
| §6 | "Reviewers MUST reject PRs missing…" | (already MUST — good) — but add: "CI **MUST** also reject" | PR-template checkbox-presence lint |
| §7 | "case is significant" | "Scope **MUST** match `^[A-Z][A-Za-z0-9-]*(\\+[A-Z][A-Za-z0-9-]*)*$`" | regex on commit subject |
| §7 | (no grammar pinned) | "Permitted scope chars: `[A-Z][A-Za-z0-9-]` + `+` separator. `/`, `#`, `_`, lowercase first char are **REJECTED**" | regex |
| §8 | "Never close an Issue without flipping aidocs/16 to done" | "Issue close webhook **MUST** verify aidocs/16 Status == done; reject otherwise" | webhook check (deferred) |
| §9 | "auto-add-to-project rule" (description) | "The board **MUST NOT** accept manual card additions; only auto-add" | GH Project setting |
| §10 | "Every Issue carries at least one label per axis" | "PR template **MUST** enforce one-label-per-axis via dropdown" | issue-template `validation: required` |
| §11 | "Reviewer gates on green CI + zero new CVSS≥7" | (already MUST in CLAUDE.md "Always: keep the security gates green") — cross-cite | dep-check exit code |
| §13 | "Never file a public Issue for a security finding" | "Issue template `security-finding.yml` **MUST** have `private: true`; the public template **MUST NOT** accept security keywords" | issue-template validation |
| §14 | "answerable in under a minute" | "`scripts/trace-feature.sh` **MUST** return in <5s wall time on the canonical repo (current p50: 0.28s)" | timing assertion |
| §15 anti-pattern #4 | "PR title without Conventional Commits scope" | "CI **MUST** reject PRs whose title doesn't match the §7 regex" | regex on PR title |

---

## The one endpoint that needs a design doc before anyone touches it

**`scripts/build-traceability-index.py`** (per `aidocs/platform/106
§3 Option A`).

This is the canonical traceability index — it'll feed:
- `docs/reference/traceability.md` (auto-regenerated)
- CI scope-lint
- Potentially a `GET /v2/admin/traceability/{id}` REST endpoint later

**The wire shape needs to be pinned now**, before three downstream
consumers each pin their own slightly-different shape. The JSON
example in `aidocs/platform/106 §3.1` is a starting point but:
- No version field (the shape will evolve)
- No `last_validated_at` timestamp
- No `orphan_commits` array (commits whose scope doesn't match any
  known ID — these are the ones release-notes auto-render miscategorises)
- No `unresolved_ids` array (IDs in aidocs/16 that have ZERO commits)

**Recommendation.** Promote `aidocs/platform/106` from `stage: idea`
to `stage: feature-defined` with the JSON schema pinned, BEFORE
GH-PM6 (or whoever) writes the implementation.

---

## Top 3 changes for developer experience (persona summary)

1. **Pin the scope-grammar regex in §7** as a single source of truth.
   Today it's implicit in `trace-feature.sh`; promote it to the
   policy. **Cost-of-being-wrong: HIGH** — silent miss on case-skew
   typos.

2. **Collapse `status:` ↔ `stage:` into one axis** (drive `status:`
   from `stage:` via a Projects v2 automation rule). **Cost-of-
   being-wrong: MEDIUM** — manual-label drift is a slow leak.

3. **Add the four missing surfaces** (aidocs/44, open PRs, plugin
   docs, docs/reference) to `trace-feature.sh` so the §14 SLA is
   "fully indexed" not "indexed across the most-active 9 surfaces."
   **Cost-of-being-wrong: LOW** — but cheap to fix.

---

## Final verdict

The GH-PM policy is **the smallest API that solves the traceability
problem at this scale**. Most of the "should"s in the doc are
*social* (reviewer discipline) where they should be *mechanical*
(CI lint, regex, schema validation). The wire shape is right; the
enforcement is loose.

Adopt with the edits above. Land the CI lint when
`aidocs/platform/106 Option A` ships. Keep the board manual. Don't
auto-file Issues. Backfill via dry-run script. The contract is
sound — just make every "should" that *can* be machine-checked,
**MUST**-checked.
