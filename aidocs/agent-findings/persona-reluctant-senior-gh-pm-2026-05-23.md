# Reluctant Senior Researcher — audit of `aidocs/strategy/85`
**Persona.** 28 years at DLR. 40 TB on shared NFS. 600-row Excel master.
**Date.** 2026-05-23.
**Subject.** The GH-PM policy doc and its six adoption questions.

---

## How I read this

I sat down Tuesday morning, coffee, and read this thing
top to bottom. I am the operator who has to live with it.
I am not the consultant who wrote it.

What I see is a beautiful policy. Nine surfaces, all
synchronised, all consistent, all proving themselves to
each other. A unified state machine that maps byte-for-byte
across three places. Auto-rendered release notes. A nine-step
traceability query.

Beautiful. Now let me tell you what's going to actually
happen at my desk.

I have data. I have results. I have a deadline. I will do
**exactly as much of this process as the gate forces me to
do**, and not one click more. Every minute spent maintaining
a label axis is a minute not spent on the engine that just
went bang. So my audit is not "is this clever?" — it
obviously is. My audit is **"will I do it on a Tuesday?"**

---

## The six questions

### Q1. Adopt the policy as-is? — Claude leans yes.

**Finding.** The policy is internally consistent and the
gates are sensible. The 4-gate Issue filter (§3) is the
single most important sentence in the document — it tells
me I am NOT being asked to mirror 360 backlog rows into
Issues. That alone makes it survivable.

**Recommendation.** **A — adopt as-is**, with the caveat that
§7 commit-scope discipline (see Q-grill below) is the most
likely point of failure. I'd ship the policy and watch §7
in production for two release cycles before declaring
victory.

**Veteran voice (≤15 words).** "Fine. The 4-gate filter
keeps me out of Issue-ticket hell. I'll sign."

**Cost of being wrong.** If the policy is adopted and §7
slips, traceability quietly degrades. Operator friction stays
flat (nobody notices). Slow leak, not a bang.

---

### Q2. First milestone `v6.0.0-rc.1`. — Claude leans yes.

**Finding.** A milestone tied to a coherent feature set
(post-MFFD-import + Garage + warmup) is the right shape.
Calendar milestones are the trap; this isn't one.

**Recommendation.** **A — yes, scope as proposed.**

**Veteran voice.** "Milestone = stuff that ships together.
Not a calendar. Fine."

**Cost of being wrong.** Low. If the bundle slips, the
milestone shifts by name; no operator pain.

---

### Q3. Backfill — dry-run plan first. — Claude leans yes.

**Finding.** Yes. Always dry-run a backfill. I've watched
mass-migrations on Tuesday mornings before. They never end
well. A dry-run that prints "I would file 47 Issues, here
are the IDs, hit y to proceed" is the only way I trust this.

**Recommendation.** **A — dry-run mandatory.**

**Veteran voice.** "If you mass-file 360 Issues on my repo
without a dry-run, I will revert it myself."

**Cost of being wrong.** High if skipped. Hundreds of
phantom Issues become permanent noise; can't easily mass-close.

---

### Q4. Auto-file Issues for in-flight agents? — Claude leans no.

**Finding.** No. The 4-gate filter (§3) explicitly says
"File an Issue ONLY when…" and lists in-flight agents as
one of the four. **One of four**, not "always." If every
agent dispatch opens an Issue, we're back to ticket-hell by
the back door.

What I actually want: the agent's worktree branch + the
PR are the public ledger. An Issue adds nothing unless an
external watcher needs the thread.

**Recommendation.** **B — keep it manual / opt-in.** The
human dispatching the agent decides whether to open the
Issue. Default off.

**Veteran voice.** "An agent does not need a ticket. The
PR is the ticket."

**Cost of being wrong.** Medium. If we auto-file, we get
50+ Issues per quarter from agent runs. Each one needs
labels, status flips, eventual close. Pure friction.

---

### Q5. Projects v2 board — manual vs scripted. — Claude leans manual.

**Finding.** Manual setup with a runbook
(`docs/ops/github-projects-board-setup.md`) is correct.
The board is a view, not a source. Scripting board mutations
adds yet another surface to keep synchronised, and the
GitHub Projects v2 API is famously a moving target.

**Recommendation.** **A — manual, per the runbook.**

**Veteran voice.** "The board is a window. Don't automate
the window. Automate the data behind it."

**Cost of being wrong.** Low. If we script later, fine;
manual now costs one operator afternoon per quarter.

---

### Q6. PR-scope hook — install vs trust. — Claude leans trust.

**Finding.** A pre-commit hook that rejects PRs without
`<type>(<ID>):` will trigger every time I cherry-pick a
fix at 11 PM and forget the scope. Then I'll learn to
`--no-verify` and the hook does nothing.

**Trust** plus a reviewer-check is the right shape **for
now**. Revisit if scope-typos are actually showing up in
release-notes "uncategorised" buckets after two RC cycles.

**Recommendation.** **B — trust + reviewer-check; defer hook.**

**Veteran voice.** "Pre-commit hooks I bypass at midnight.
Reviewer checks I cannot."

**Cost of being wrong.** Low–medium. A few uncategorised
commits per release. Fixable with a one-line release-notes
edit. If the rate climbs above ~5%, install the hook.

---

## Grilling the specific clauses

### §7 — Conv-Commits scope discipline

> "I'm supposed to remember the aidocs row ID for every commit?
> When the row's title changes mid-PR, what then?"

This is my biggest concern. **Row IDs are stable; titles
are not.** As long as the policy is "scope = ID" and never
"scope = title-slug," I can live with it. The scope is
seven characters of opaque ID. That's fine. Muscle memory
will form.

**What I would push back on.** The case-sensitivity rule.
`feat(fs1b)` vs `feat(FS1b)` — if TRACE-A misses the commit
because I shifted-down at the wrong moment, that's a
fragile foundation. Either make the matcher case-insensitive
or run a normalisation pass in `build-traceability-index.py`.

**Recommended edit to §7.** Make the matcher
case-insensitive. The IDs are unique even lowercased.

---

### §2 — The unified state machine

> "Now I have to remember which stage a doc is in AND a
> backlog row's status AND a label?"

No. The clever thing about §2 is the **byte-for-byte
mapping**. I only have to remember ONE thing — the stage —
and the other two surfaces are derived. If the mapping
table is enforced (and §15 antipattern 6 says it is), then
I only ever edit the stage in the doc front-matter and the
backlog row status; the label is sync'd from
`.github/labels.yml`.

**My remaining worry.** When does the label actually get
applied to an Issue? §3 says Issues are rare. So in practice,
the label axis is invoked maybe ten times a quarter. Fine.

**Recommendation.** Keep §2 as-is, but the policy should
state: **"the stage in the doc front-matter is the source;
backlog status and label follow"** — not "all three are
synchronised." A primary source breaks the appearance of
three-way symmetry and clarifies who leads.

---

### §14 — The traceability query

> "Nine bash commands? When do I actually run that vs.
> just ask a colleague?"

I will never run this script unless I am preparing for an
audit. Asking a colleague is faster on a Tuesday morning by
a factor of ten. **But that's fine** — the script exists for
the auditor, not for me. The auditor is the user.

**What's missing.** A "trace-feature.sh --html" mode that
produces a one-page HTML summary the auditor can save as
PDF. The shell-script output as-is is a wall of grep noise.

**Recommendation.** Add a `--report` flag that renders to
markdown. Defer the HTML mode.

---

### §3 — The 4-gate Issues filter

> "When the gate 'currently being executed by an agent'
> fires for what an AI is doing, is that for me or for
> everyone else?"

Reading carefully: it's for **everyone else**. The Issue
acts as the public in-flight ledger so an outside watcher
can see what the agent is doing. For me, internal,
disinterested — I do not need the Issue. The agent's
worktree branch + the PR carry everything I want.

**Recommendation.** Tighten the language in §3 gate 4:
"In-flight agent execution **AND** at least one of: (a)
external visibility needed, (b) cross-team coordination,
(c) audit checkpoint." Otherwise gate 4 is too permissive
and Q4's lean-no slips back in.

---

## What I'd cut

Honest list of "nice to have, won't survive contact with a
Tuesday":

1. **The "three saved views" on the Projects v2 board (§9).**
   Two views suffice: "active" + "recently shipped." The
   external-pickup view doesn't earn its keep until GH-INFRA4
   triggers anyway. Add it then.
2. **The full nine-surface traceability script (§14) as a
   user-facing thing.** Keep the script; mention it once in
   the policy; don't put it on the front page. It's an
   audit tool.
3. **The release-body requirement to cite EVERY aidocs/16
   row ID + matching aidocs/34 row (§12 step 1+2).** Cite
   them, yes. But auto-fill from the milestone's bundle
   list — don't make the operator paste them by hand.
4. **The label-axis mandate of "at least one per axis"
   on every Issue (§10).** Realistic only if defaults are
   applied automatically by the Issue template. Make the
   template do the work; don't put the load on the human.

---

## What would have to be true for me to actually use this

Five conditions:

1. **The Issue gate stays narrow.** §3 holds firm; nobody
   sneaks in "auto-file all agent runs" by the back door.
   If Issues stay rare (single digits per quarter), I won't
   feel the policy at all on most days.
2. **Conv-Commits scope is case-insensitive at the matcher.**
   I will mis-type case at midnight. I refuse to be punished
   for it.
3. **The PR template defaults the row ID, doesn't make me
   look it up.** A drop-down or a "pick from open milestone"
   selector is the difference between a 5-second tax and a
   60-second one.
4. **`trace-feature.sh` works on the first try without me
   reading docs.** One positional arg, sensible output, no
   tooling install.
5. **The reviewer catches scope violations, not a hook.**
   Hooks I will `--no-verify` past at 11 PM. A reviewer
   asking nicely will land the change.

If those five hold, this policy is fine. If any one of them
slips, I will quietly drop the discipline and you'll catch
me three releases later when TRACE-A is full of orphans.

---

## Overall verdict

**I'd adopt this**, with the §7 case-sensitivity edit and
Q4 firmly no (manual / opt-in agent Issue filing). The
4-gate filter in §3 is what makes this survivable; do not
soften it. The traceability script is for auditors, not for
me — keep it; don't put it in my face.

What I most appreciate: the policy says explicitly **"the
360+ rows do NOT get back-filled into Issues."** That
sentence is the difference between "I'll follow this" and
"I'll find a way around it."

What I most worry about: scope discipline drift in §7.
Watch it. Two release cycles. If it slips, install the hook.

— The persona, 28 years in, going back to my Excel sheet.
