---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# v15 import — review from the reluctant senior researcher

*28 years at DLR. 40 TB on NFS. 600-row Excel master sheet. Has never trusted an
RDM system. Read v15's design doc, the forging memory, the f(ai)²r design, and
the showcase README before writing this.*

---

## What v15 lets me do that my folder structure can't (real answer, not marketing)

Three things, honest:

1. **One link per DataObject that survives a rename.** Stable `appId` decoupled
   from path. My scripts won't break when I reorganise. My folder tree + Excel
   can't do this.

2. **Predecessor walk reachable from any leaf** (§13). The `tapelaying/Track 244
   (Run 30239)` directory does not know it descends from PlyGroup-3 → Ply-12.
   My Excel *thinks* it knows because I typed it. v15 lifts the edge from the
   source — the lineage becomes evidence, not my opinion.

3. **A timestamped record that "this dataset was lifted from cube3 at 23:17 UTC
   on 22 May 2026 by `agent:claude-opus-4-7` on behalf of fkrebs."** The
   `fair2r:AuthoringPass` per batch (§10) is the thing I don't have. My cube3
   folder copy has an `mtime` and nothing else.

Notice what's not on the list: the 5-tuple TS identity, the semantic
annotations, the Trace3D demo. Those are consequences of being in Shepard,
not differentiators of v15 over my folders.

---

## What v15 makes WORSE than my current setup

1. **Naming.** Cube3 has `name = "tapelaying/Track 244 (Run 30239)"`. My Excel
   has "TR-244-LH2-2026-04". v15 says nothing about which one ends up in
   `name` on the dest. If it copies cube3 verbatim, I've lost the only
   convention my collaborators know. §4's bug table doesn't list this
   because it isn't an API bug — it's a design omission. Silent
   corruption-of-meaning.

2. **The dest is now canonical; v15 says nothing about backing it up.**
   §1 says the on-disk drop is shape-only (TS placeholders 0-byte). If
   nuclide.systems goes down for a week, where's my data? My NFS has
   tape rotation. v15 needs a sibling op for `pg_dump` + Garage snapshot
   + `cypher-shell --dump`. Missing.

3. **Pre-flight abort leaves the dest dirty.** §9's "detect → print
   runbook → exit cleanly" is fine if the probe runs *before* writes.
   If a sidecar restarts mid-run, the state file claims DO N but the
   dest is at DO N + ε (metadata posted, file upload pending). §5's
   "record as failure, continue queue" leaks partial DOs. Solvable
   (per-DO atomic boundary) — not promised.

4. **The 14-hour JWT.** §1 pauses on 401 and waits. The script tells
   me 5 minutes later via the log re-upload thread. If I'm asleep
   that's an 8-hour delay. `rsync` doesn't need a JWT.

5. **The script verifies itself.** v14 had silent Bug E corruption of
   structured data. v15's ETA + "8383/8383" both come from the same
   code paths that under-counted before. Where is the *independent*
   verifier that walks the dest and checks `fileSize > 0`,
   `payload IS NOT NULL`, `timeseries[] non-empty` separately? That's
   the v14 bug pattern repeating.

---

## The AI-annotation question — does this earn my trust?

**No, not as designed.**

The design is structurally right: `fair2r:AuthoringPass`,
`prov:wasAssociatedWith agent:claude-opus-4-7` + `usr:fkrebs-at-nucli-de`,
`fair2r:verificationState verif:unverified` on every entity. Fine.

But the result is **8383 DOs all marked 🤖, none verified**. Every node
carries a "needs review" flag, no human will review 8383 nodes, the flag
becomes wallpaper. After three weeks it's indistinguishable from no flag.

What I'd actually trust:

1. **Sampling with propagation.** "Of 8383 DOs, here are 30 sampled
   by ply position + track range + anomalous-attribute distribution.
   Verify these, the verdict propagates to the cluster." Without
   propagation, manual review is theatre.

2. **Script-level trust.** Inspect v15 source once → grant the whole
   batch `fair2r:wasAcceptedAs auto-applied`. Memory
   `project_ai_human_collab_provenance.md` already has this level;
   the UI doesn't promote it yet. I want a forging-stage verdict,
   not 8383 row-level verdicts.

3. **Differential review on the next pass.** v15 = pass 1. Future
   AI re-org = pass 2. Review the **diff** between snapshots, not
   the artefacts. Diff is the unit of trust.

Until "what do I do with 8383 unverified 🤖 DOs?" has an operational
answer, the badge is honest but useless.

---

## The one demo moment that would convince me

Show me, in five minutes, without typing:

> *"Here is your dataset. Here is the snapshot taken the moment v15
> finished. Here is the SHA-256 of the snapshot manifest, pinned to
> git commit `abc1234`. Six months from now, an auditor asks 'what
> was in TR-244 on 22 May 2026' — you run `shepard restore --snapshot
> mffd-as-imported-2026-05-22` against a fresh instance, you get
> bit-for-bit the same graph back: predecessor edges, file SHA-256s,
> TS point counts. The snapshot is your evidence."*

That's the **ledger-anchoring** moment: third-party-verifiable (manifest
hash in git) + cold-iron-restorable (substrate documented). §13's
acceptance criteria don't say whether v15's snapshots are *that kind*.

If yes — sold. If no — I run NFS backup in parallel for two years
before I trust nuclide.

---

## Minimum changes to v15 that would make me actually run it on real data

Priority order. None are big.

1. **Independent verifier pass** — separate script or `--verify` flag
   that walks the dest from outside the import counters, asserts §13
   per batch. Fail loud. Don't let the producer grade itself.

2. **Name-mapping input** — `--names map.csv` of `(src_appId, dest_name)`.
   Default copies cube3 name; override aligns to my Excel taxonomy.
   Cube3 name preserved as an attribute regardless.

3. **JWT-expiry notification that wakes me** — webhook / email /
   matrix. 5-minute log re-upload is fine for forensics, terrible
   for "the script is paused, please come."

4. **Document the backup story** in §93. `garage block resync` +
   `pg_dump shepard` + `cypher-shell --dump`. The on-disk drop is
   not a backup. One cron recipe.

5. **Per-DO transactional boundary** — if file upload fails after
   metadata POST, either roll back metadata or emit `partial:<appId>`
   to the state file. §5's "record as failure, continue queue" leaks
   partial DOs invisibly.

6. **Snapshot-and-anchor on completion.** §13 should add: *"✓ snapshot
   `mffd-as-imported-<session>` created; manifest SHA-256 printed
   to stdout; `git tag` recipe emitted."* The forging-stage seal.

---

## Honest verdict — would I run v15 against MY data tomorrow? Why or why not.

**No.** Not tomorrow. Closer than I expected.

Closer because:
- §4's 8-bug catalogue (Bug E = silent structured-data corruption in
  v14, labelled CRITICAL) is the kind of honesty I look for.
- Bug I's `predecessorIds` fix means the DAG actually replicates.
  Without that, the whole exercise is pointless.
- §5's redeploy-resilient long-wait shows someone thought about what
  happens when the dest goes down mid-import. Most importers don't.
- The forging memory shows snapshots are understood as evidence, not
  backups. Rare.

Still no because:
- Producer grades itself. No independent verifier.
- No backup story for the dest. My NFS has tape rotation.
- 8383 unverified 🤖 DOs is wallpaper without sampling or script-level
  acceptance.
- Name mapping isn't in scope; my taxonomy will silently vanish.
- §13 doesn't say whether the snapshots are ledger-grade.

What I'd actually do: run v15 against **one PlyGroup** (PlyGroup-3, say,
4 plies × 50 tracks ≈ 200 DOs). Spend an afternoon walking it manually
against cube3. If every track's `predecessorIds`, file SHA-256, and TS
point count match — *then* run the rest with the verifier as a sidecar.

Add the independent verifier and the snapshot-tag recipe to §13, and I
move from "no" to "yes, on the 200-DO sample, then we talk." Today,
"no — too much script-trusts-itself."

— *the reluctant senior, 22 May 2026*
