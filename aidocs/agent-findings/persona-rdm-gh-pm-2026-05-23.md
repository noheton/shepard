---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Persona review — Research Data Manager / FAIR Steward
# on the GH-PM1 policy + this-session deliverables

**Persona.** Lead Research Data Manager and FAIR Data Steward. Lens:
DFG / Horizon Europe / Clean Aviation JU funding mandates, FAIR
principles (F/A/I/R), DataCite/DOI, embargo / restricted access,
RO-Crate, audit trails for funding reviewers and journal
data-availability statements (Nature Sci Data, Sci Data, Data in
Brief, JOSS).

**Subject.** `aidocs/strategy/85-github-project-management-policies.md`
(the GH-PM1 policy), the new `CITATION.cff` at repo root, the
`docs/bibliography.md` + `docs/_data/references.bib` (585 lines, BibTeX
SSOT), `aidocs/data/00-model-inventory.md`, and the `scripts/trace-feature.sh`
helper. The six adoption questions (Q1–Q6) on the table for the
maintainer.

**Author.** RDM persona, dispatched 2026-05-23.

---

## §1 — What I read and ran

- `aidocs/strategy/85-github-project-management-policies.md` (the full
  policy — 16 sections).
- `CITATION.cff` (cff-version 1.2.0, fork-author block + upstream
  reference block, Zenodo DOI inherited).
- `docs/bibliography.md` (Jekyll-rendered) + `docs/_data/references.bib`
  (BibTeX SSOT, 585 lines, six categories).
- `aidocs/data/00-model-inventory.md` (substrate + label inventory).
- `aidocs/agent-findings/research-data-manager.md` (prior RDM finding —
  the FAIR gap analysis the team commissioned earlier).
- Ran `scripts/trace-feature.sh GH-PM1` against the live worktree.
  Surface 1, 2, 4, 5 resolve; surfaces 3 (persona findings), 6
  (aidocs/34 row), 8 (Issue), 9 (release tag) return empty for GH-PM1
  itself — i.e. the policy doc is **not yet fully traceable by its own
  rules**. The empty surfaces are expected for an unreleased,
  Issue-exempt, non-admin-visible doc-only change; the test is whether
  a reviewer can tell. Today, they cannot — the script prints
  `(no aidocs/34 row — admins don't know this shipped)` without
  distinguishing "missing because not required" from "missing because
  the rule was broken." This is a small but real audit-trail gap;
  noted under §5 below.
- Sampled `FS1b`, `IMPORT-W1`, `UH1a` rows in `aidocs/16-dispatcher-backlog.md`
  for the per-row narrative depth the policy assumes. Confirmed: rows
  carry the design-doc citation, ship-commit, test count, and
  trade-off note. Good.

---

## §2 — Verdict on the six adoption questions

| Q | Topic | Claude's lean | RDM vote | Cost-of-wrong (research-integrity) |
|---|---|---|---|---|
| Q1 | Adopt policy 85 as-is? | yes | **A — adopt** | Wrong choice = no audit trail when reviewer asks "show me the provenance of feature X". Adopting is the lowest-risk move. |
| Q2 | First milestone `v6.0.0-rc.1` scope this session | yes | **A — yes** | Wrong = release notes never cite aidocs/16 IDs; data-availability statement cannot point at a versioned artefact. |
| Q3 | Backfill = dry-run plan first | yes | **A — yes** | Wrong = retroactive Issue spam pollutes the audit trail; reviewers cannot tell which Issues are real triage vs. ledger reconstruction. |
| Q4 | Auto-file Issues for in-flight agents | no | **B — no, but with a caveat** | Wrong (auto-file=yes) = MFFD-data Issues leak DLR-internal context onto public github.com. See §4. |
| Q5 | Projects v2: manual vs scripted | manual | **A — manual** | Wrong (scripted=yes) = an inconsistent scripted populate creates "what changed" noise on the board, hides real card moves. |
| Q6 | PR-scope hook: install vs trust | trust | **A — trust + provide-hook** | Wrong (mandatory hook) = friction for external contributors at the exact moment we want the velocity gate (GH-INFRA4) to open. Ship the hook as opt-in (GH-PM3 already does this). |

**Six-for-six concurrence with Claude's leans.** No dissent.

---

## §3 — Per-question reasoning (FAIR lens)

**Q1 — Adopt as-is.** A funding reviewer auditing "trace feature X
end-to-end" against this fork in 2027 will get a complete answer in
under a minute via `trace-feature.sh`. That meets the **Horizon Europe
Open Science** clause on reproducibility-of-claim and the **DFG
Guidelines for Safeguarding Good Research Practice (2019, §13)** on
documentation completeness. Adopt.

**Q2 — `v6.0.0-rc.1` scope this session.** A milestone is the unit
that pairs with a release tag. A release tag is the unit a
data-availability statement cites. The shipped scope (Garage S3,
MFFD-import, smart-warmup) is coherent enough to be the first
auditable release. Cut it. *FAIR mapping:* tag → versioned identifier
→ persistent reference for the Nature Sci Data DAS template (see §6).

**Q3 — Backfill via dry-run.** The 4-gate rule means the backlog
itself does NOT get back-filled into Issues — only the work that hits
gate 1/2/3/4 retroactively. The dry-run plan must enumerate which
existing rows trigger which gate; otherwise mass-filing is silent.
A reviewer comparing the GitHub Issue count to the aidocs/16 row count
will see the 4-gate filter is working when the ratio stays in the
"single digits per quarter" range the policy promises (§3 last
paragraph). Dry-run first protects that signal.

**Q4 — Don't auto-file Issues for in-flight agents.** Two reasons,
both FAIR-grounded:
1. **Embargo / restricted-access leakage.** MFFD data is DLR
   industrial IP (Premium AEROTEC + DLR-ZLP joint context). Auto-filed
   Issues on `agent-{slug}` worktrees will contain agent prompts that
   echo internal context. Even with private-repo Issues, the prompt
   transcript is the audit trail (see `project_provenance_principle.md`
   in MEMORY), which means a leak of one Issue body = leak of the
   reasoning chain. The 4-gate rule (gate 4 "in-flight agent
   execution") makes Issues **optional**, not automatic; keep it that
   way.
2. **Signal-to-noise.** Agents fan out hundreds of worktree branches
   per active session. Auto-filing turns the Issues tab into a tail
   of agent runs. A reviewer scanning Issues to triage "what's the
   project working on this week" sees noise, not signal. The
   manual-file gate forces the human to decide *this agent run is
   worth a public ledger*; almost none are.

**Q5 — Projects v2 manual.** Scripted populate breaks the §15 rule
"the board is not a substitute for aidocs/16." A scripted hand keeps
populating; the SSOT drifts because nobody curates the cards. Manual
populate puts a human in the loop at exactly the gate where it
matters (the Issue → card moment). Keep manual.

**Q6 — Trust + ship-the-hook.** The §7 strict-uppercase scope rule is
a real risk (a single `feat(fs1b)` typo makes TRACE-A miss a commit
for life). But making the hook mandatory is the wrong fix — it shifts
the cost onto external contributors at the velocity moment we want
unblocked. The right shape is **opt-in hook with a CI catch-net**: the
hook ships under `scripts/hooks/`, `make install-hooks` wires it in,
CONTRIBUTING.md recommends it, and a release-time check (per §5
pre-flight item 4) catches typos that slipped through. GH-PM3 already
sketches this; trust the policy.

---

## §4 — FAIR-completeness verdict on the audit trail

**One-line verdict.** The GH-PM1 chain (aidocs/16 → branch → PR →
commits → aidocs/34 → aidocs/data/00 → release tag → release notes)
is **a FAIR-grade traceability spine** for fork-internal provenance;
the `CITATION.cff` + `docs/bibliography.md` + TRACE-A index together
are **a FAIR-grade citation surface for external reuse**; the gap is
**research-integrity-grade evidence linking the two** (next §5).

### What works

- **Findable.** TRACE-A surface 1+2+4 (aidocs/16 row + design doc +
  scoped commits) gives any reviewer a query path from feature name
  to source-of-truth in one shell call.
- **Accessible.** Public GitHub repo + Pages site + Zenodo-deposited
  upstream DOI means an external auditor needs no DLR credentials to
  audit the chain. Private security advisories are walled per §13.
- **Interoperable.** Conventional Commits + RFC-7807 envelopes on
  REST + BibTeX SSOT for citations + JSON-LD on the Unhide feed
  means downstream tools (Zotero, OpenAIRE, HKG harvest) ingest
  without bespoke parsing.
- **Reusable.** CITATION.cff carries ORCIDs, license (Apache-2.0),
  affiliation, upstream-fork relationship → the upstream chain is
  preserved (not erased). This is the rare case where a fork
  citation does the right thing by extending, not replacing, the
  upstream author list. Material contribution.

### FAIR-completeness gaps

1. **No `license` / `embargo` / `funder` / `grantId` on the data
   model itself** (per prior RDM finding `research-data-manager.md`).
   The audit-trail chain documents *the fork's development*; it does
   not document *the data shepard manages*. A Horizon Europe reviewer
   auditing a Collection of MFFD process data wants to see (a) which
   grant funded its capture, (b) what licence it carries, (c) when
   the embargo lifts. None of those fields exist yet (KIP1e + UH1e
   shipped via §3 policy but the *data fields* are blocked on a
   separate row). **The GH-PM1 audit trail tracks the platform, not
   the science.** Flag this in any DFG report that cites the
   traceability surface; do not over-claim.
2. **TRACE-A surface 7 (aidocs/data/00 delta) is grep-by-ID.** If a
   commit forgot to reference its row ID in the model-inventory
   diff, the surface returns empty silently. The §5 pre-flight check
   only catches missing aidocs/34 rows, not missing aidocs/data/00
   updates. Recommend: add `aidocs/data/00-model-inventory.md` to
   the same release-block check (§5 pre-flight item 1 already
   guards `aidocs/34`; mirror it for `aidocs/data/00`).
3. **Persona-finding surface (TRACE-A #3) has no schema.** The
   `aidocs/agent-findings/` directory is the persona-review trail; a
   reviewer assessing whether feature X was reviewed needs to know
   *who reviewed it*. Today the agent-findings filenames carry the
   persona slug (`persona-rdm-gh-pm-2026-05-23.md`) but the row in
   aidocs/16 does not link back. Recommend: per-row `Personas:`
   column (or front-matter field) listing the persona slugs that
   audited the row. Light-touch fix.
4. **Release-notes citation depth.** §12 says the release body
   cites aidocs/16 row IDs + aidocs/34 rows. It does NOT require
   citing the **persona findings** or the **bibliography entries**
   that motivated the work. For Nature-grade publication evidence,
   we want both. Recommend: extend §12 step 4 to "link the persona
   findings + bibliography keys that motivated the work."
5. **The CITATION.cff has no `commit` hash field.** GitHub's "Cite
   this repository" UI renders the latest tag; a reviewer citing
   the fork at a specific dev-state needs commit-pinned citation.
   The CFF spec supports `commit:` — recommend adding it on every
   release tag.
6. **No RO-Crate at release-tag boundary.** Horizon Europe DMP
   compliance increasingly requires an RO-Crate manifest at the
   release artefact level (the SBOM is the dependency side; the
   RO-Crate is the **research-output** side). Recommend: add
   RO-Crate generation as a §5 pre-flight item for any release that
   touches user-visible payload kinds. Backlog row.

---

## §5 — GH-PM1 self-audit (the policy's own traceability)

`bash scripts/trace-feature.sh GH-PM1` returns:

- Surface 1 (aidocs/16): **PASS** — three rows (GH-PM1, GH-PM2, GH-PM3).
- Surface 2 (design doc): **PASS** — `aidocs/strategy/85-…md`.
- Surface 3 (persona findings): **EMPTY** — will be populated by this
  file once committed.
- Surface 4 (commits): **PASS** — `a17ad0cc`.
- Surface 5 (files touched): **PASS**.
- Surface 6 (aidocs/34 row): **EMPTY** — admin-invisible doc-only
  change; correctly empty by §3 gate rules, but the script cannot say
  so without a "this surface intentionally empty" annotation. Audit
  trail UX defect, not a chain break.
- Surface 7 (aidocs/data/00): **EMPTY** — correct, no model touch.
- Surface 8 (Issue): **EMPTY** — correct per §3 (no external pickup,
  no security, no bug, no in-flight agent before this audit).
- Surface 9 (release): **EMPTY** — correct, unreleased.

**Recommendation.** Enhance `scripts/trace-feature.sh` to emit a
"(legitimately empty — not admin-visible)" diagnostic when a surface
returns no rows AND the aidocs/16 row's `Notes` column indicates the
change is doc-only / chore-only / Dependabot. Cheap; closes the
"silent absent vs. missing-rule" audit defect.

---

## §6 — Data-availability-statement template (ready-to-paste)

For a Nature Sci Data submission citing a Shepard-managed dataset and
the platform itself:

> **Data availability.** The dataset analysed in this paper is
> managed by Shepard (DOI: 10.5281/zenodo.5091603, fork:
> noheton/shepard v6.0.0-rc.1, commit `<COMMIT_HASH>`). The
> end-to-end provenance chain for every feature this paper relies on
> can be traced via the public command
> `scripts/trace-feature.sh <FEATURE-ID>` against the published
> repository (https://github.com/noheton/shepard). Specifically: the
> feature IDs `FS1b` (S3 file storage), `IMPORT-W1` (smart-warmup
> v15.2), and `UH1a-d` (Helmholtz Unhide feed) are cited in this
> paper; each resolves to (i) a backlog row at
> [aidocs/16-dispatcher-backlog.md](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md),
> (ii) a design document under `aidocs/`, (iii) reviewed pull
> requests, (iv) an admin-facing change log at
> [aidocs/34-upstream-upgrade-path.md](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md),
> and (v) a model-inventory delta at
> [aidocs/data/00-model-inventory.md](https://github.com/noheton/shepard/blob/main/aidocs/data/00-model-inventory.md).
> The Shepard software is citable via the repository's
> `CITATION.cff` (GitHub "Cite this repository" UI). All third-party
> standards, ontologies, and prior art cited in Shepard's design
> notes are catalogued in the bibliography at
> https://noheton.github.io/shepard/bibliography/.

This template is **ready-to-paste**; replace `<COMMIT_HASH>` and the
list of feature IDs with the paper's actual usage. The persistent
surface for this statement is the GitHub Pages site (Apache-2.0,
externally indexable, no auth required).

---

## §7 — Recommendations to the maintainer (priority order)

1. **Ship Q1+Q2+Q3+Q4-no+Q5-manual+Q6-trust as the adoption answer.**
   No dissent from the RDM lens. (One commit, this session.)
2. **Extend `scripts/trace-feature.sh`** to distinguish "empty
   because exempt" from "empty because missing." 10-line patch to the
   existing helper. Closes §5 audit-UX gap. (One commit, next session.)
3. **Add `aidocs/data/00-model-inventory.md` to the §5 pre-flight
   release block.** Mirror the existing aidocs/34 check. (Edit
   `aidocs/strategy/85 §5`, ship in same PR as the next release-cut.)
4. **Add a `Personas:` field per aidocs/16 row** linking the persona
   findings that audited it. Backlog row, gated on the dispatcher
   format refactor.
5. **CFF `commit:` field on every release tag.** Trivial — add to the
   release runbook (`docs/ops/cut-a-release.md`) step list.
6. **RO-Crate manifest at release-tag boundary for user-visible
   payload-kind releases.** Backlog row. Gated on `shepard-plugin-publisher`
   landing (research-data-manager.md prior finding).
7. **License / embargo / funder / grantId fields on Collection +
   DataObject** — out of scope for GH-PM1 but the highest-leverage
   FAIR gap on the platform; the audit trail this session shipped
   *enables* this work but does not *fulfil* it. Don't conflate the
   two in funding-body reporting.

---

## §8 — One-paragraph summary for the maintainer

GH-PM1 is the right shape. Adopt it. The chain it creates is
FAIR-grade for **fork provenance**, not yet for **dataset provenance**;
keep the two distinct in funding-body reporting. Concur with Claude
on all six adoption questions. Top three things to add next, in order:
(a) extend `trace-feature.sh` to label intentionally-empty surfaces;
(b) add `aidocs/data/00` to the release pre-flight block; (c) ship the
`Personas:` link from aidocs/16 rows to persona findings. The data
availability statement template in §6 is paste-ready for the first
publication that depends on this audit trail.
