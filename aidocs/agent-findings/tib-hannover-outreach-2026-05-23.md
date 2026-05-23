---
title: TIB Hannover (OntoAligner) outreach — Step 1 "Used by" downstream entry
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributor, strategy
---

# TIB Hannover outreach — Step 1 ("Used by" entry)

Per `aidocs/agent-findings/persona-audit-ontology-mapping-2026-05-23.md`
ESCALATION-ONT-4 (three personas converged independently on TIB
collaboration opportunity) + user OK 2026-05-23: open the lowest-friction
contact channel first — let the upstream maintainer see Shepard exists
and adopts OntoAligner before any heavier asks.

## Target repo

**`sciknoworg/OntoAligner`** (NOT `HamedBabaeiGiglou/OntoAligner`).
TIB-Hannover-affiliated org `sciknoworg` (Scientific Knowledge Organization)
maintains the canonical repo. v1.8.0 shipped 2026-05-22.

- 71 stars, ESWC 2025 Best Resource Paper winner
- Maintainer: Hamed Babaei Giglou + Auer (TIB Hannover) + co-authors
- License: Apache-2.0
- Has `CONTRIBUTING.md`, has `Citation` section in README (line 154)
- No explicit `Adopters` / `Used by` section in README — opening that as
  an *option for them to take or leave* is the contributor-respectful move

## Issue draft (ready to submit via `gh issue create`)

**Title:** Used in production by DLR Shepard — happy to be listed if you keep an Adopters section

**Body:**

```markdown
Hi Hamed + sciknoworg team — congrats on v1.8.0 and on the ESWC 2025
Best Resource Paper award! We've adopted OntoAligner in the
[DLR Shepard](https://shepard.nuclide.systems/) research data platform
and wanted to surface our adoption + see if you'd like a downstream
adopters list.

## Adoption context

- **Project:** DLR Shepard — research data management platform with
  multi-substrate storage (Neo4j + Postgres + TimescaleDB + MongoDB +
  Garage S3) and an ontology-driven shape layer (SHACL templates,
  metadata4ing pre-seeded).
- **Use case:** AI-assisted ontology mapping for industrial aerospace
  manufacturing — specifically the CHAMEO ↔ Material OWL pair for the
  MFFD (Multi-Functional Fuselage Demonstrator) thermoplastic-CFRP
  digital thread case study.
- **Adoption shape:** OntoAligner runs as a sidecar behind a REST
  contract; the LLM-as-oracle pattern (LogMap/OntoAligner-retrieval
  candidates → LLM validation) per our acceptance ladder.
- **Design doc:** [aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md](https://github.com/noheton/shepard/blob/main/aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md)
- **Field validation:** MFFD live ingest from DLR ZLP Augsburg —
  8,400+ source DataObjects with AFP/welding process metadata, real
  industrial data not synthetic.

## Why we picked OntoAligner over alternatives

- Apache-2.0 license (compatible with Shepard's permissive posture)
- TIB Hannover stewardship — institutional alignment with our NFDI4Ing /
  metadata4ing adoption (`m4i` is already pinned in Shepard via
  ONT1b)
- The exact MSE-track / MaterialInformation ↔ MatOnto pair we need
  for CHAMEO ↔ Material OWL
- v1.8.0 dropped one day before our reuse survey — alive, maintained,
  responsive

## What we're offering

- **Public adoption signal** — a "Used by" / "Adopters" section in your
  README pointing at Shepard, if you'd like one. Happy to PR it if you
  prefer that shape; happy to be just listed if you'd rather curate it
  yourself.
- **Field feedback** — alignment-quality observations against gold
  standards as we run the MFFD pipeline; happy to file structured
  issues with reproducible examples.
- **Citation discipline** — Shepard's NFDI4Ing / Helmholtz funding
  produces citable outputs; we'd cite OntoAligner per your README
  pattern in any publication that uses it.

## What we're NOT asking for (yet)

- No request for code changes, custom features, or roadmap shifts.
- No expectation of a response within any timeline — this is "fyi we
  exist," not a support request.

Happy to provide any other context that's useful. Thanks for the
toolkit + the OAEI-LLM work behind it!

— Florian Krebs, DLR Augsburg ZLP
```

## How to submit

```bash
# preview
echo "above body" | head -50

# submit (requires gh auth)
gh issue create --repo sciknoworg/OntoAligner \
  --title "Used in production by DLR Shepard — happy to be listed if you keep an Adopters section" \
  --body-file aidocs/agent-findings/tib-hannover-outreach-2026-05-23.md
```

Or paste the body directly via the GitHub UI at
`https://github.com/sciknoworg/OntoAligner/issues/new` — same result, no
`gh` auth required.

## Follow-ups (sequenced, not committed)

If the maintainer engages positively → escalate to:
- Step 2 — direct email to Hamed (TIB) with the MFFD case study + ask
  for alignment-quality review
- Step 3 — OAEI 2026 MSE-track revival co-authoring (requires literature
  review + benchmark dataset prep + OAEI submission)
- Step 4 — ESWC 2027 / OAEI workshop co-authored resource paper

Each step is a separate decision. Step 1 is the only one approved 2026-05-23.

## Risks + counter-evidence

- **Risk: silence.** The maintainer may simply not respond. That's the
  worst case for Step 1 — we lose nothing, the adoption signal stands
  in our own docs.
- **Risk: feature request shape.** The issue may be misread as "DLR
  wants OntoAligner to do X." The "What we're NOT asking for" section
  explicitly closes that frame.
- **Risk: IP exposure.** MFFD data is DLR-internal industrial IP. The
  issue mentions the case study + the pair (CHAMEO ↔ Material OWL) but
  does NOT share any data or instance values. The IP boundary (per
  persona-audit F4.4) is "term labels only, never instance values"
  and this issue respects that.
