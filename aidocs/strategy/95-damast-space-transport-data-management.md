---
title: "DaMaST — Data Management for Space Transport (DLR cluster, 2025-2026+)"
stage: fragment
last-stage-change: 2026-05-23
audience: [thesis, strategy, contributor]
---

# DaMaST — Data Management for Space Transport

*Strategy doc anchored on a single primary source: the Krebs-authored
`20251124_Intro_Damast.pptx` (7 slides, modified 2025-11-24)
[@krebsDamastIntro2025]. DaMaST sits inside the wider DLR Raumfahrt
(space-transport) institute network and is the **second concrete instance**
of the Shepard-bearing DataHub stack outside ZLP Augsburg — the first being
the Welzmüller et al. PLUTO RDM line at Bremen
[@welzmuellerPlutoPoster2025; @welzmueller2024Pluto].*

## §1 What DaMaST is

**Full name:** *DaMaST — Data Management for Space Transport*. A DLR
internal project (Vorhaben) carried jointly across the Raumfahrt-institute
cluster, scoped at roughly half a person-year (~0.5 PJ) per annum
[slide 4 + slide 5, @krebsDamastIntro2025].

**Two-phase shape (per the deck):**

- **2025 (Vorhaben 2025, ~0.5 PJ).** Use-case selection (*STORT*
  hyperschall-experiment + *LUMEN* triebwerkstest — **`examples/lumen-showcase/`
  in this fork is the operational synthetic demonstrator of this exact
  DaMaST use-case**, not merely a namesake. Per operator clarification
  2026-05-23: *"LUMEN showcase is actually DaMaST"*. The showcase code,
  the DaMaST Vorhaben, and the 93-page DaMaST workshop protocol (Oct
  2024 Göttingen requirements workshop) are three surfaces of one
  initiative); user-requirement interviews across
  Raumfahrt-institutes AS, BT, RA, RB, RY; preparation of the 2026+
  project proposal. Slide 4 marks the 2025 proposal pass as *gescheitert*
  (failed) with stated reasons: *komplexe Interaktionen* and *offene
  Fragen bzgl. Governance*. Outcome: extension into Vorhaben 2026.
- **2026 (Vorhaben 2026, ~0.5 PJ).** Goals: build a **technology
  demonstrator DataHub** consisting of `shepard` + `databus` + MOSS — the
  same three-component stack that aidocs/strategy/90 (HMC Phase 2) and
  aidocs/strategy/88 (NFDI4Ing F-1/F-2) propose, instantiated for a
  different use-case domain. Project proposal target: mid-2026 (~6/26),
  full proposal ~10/26 [slide 5, @krebsDamastIntro2025].

## §2 Why DaMaST is thesis-relevant

Slide 5 makes the alignment claim explicit: *"für Aufmerksame: 1:1 was
in NFDI (und auch HMC) aufgebaut werden soll, aber anderer Use-Case"*
— **the same DataHub stack the federation work is building, instantiated
in the Space-Transport vertical**. That is direct evidence the
`shepard + databus + MOSS` triple has crossed from one cluster (BT/ZLP +
HMC + NFDI4Ing) into a second cluster (Raumfahrt) under the same lead
author. Three independent funding cycles converging on the same substrate
combination is a stronger claim than any single one.

The author's framing in slide 6 (*"Warum DaMaST am ZLP?"*) names four
ZLP-side benefits, of which the third is the load-bearing one for this
library: **"Etablierung shepard in DLR Bereich Raumtransport"** —
establishing Shepard in the DLR space-transport area. This is rollout
work, not exploratory work; the author is treating Shepard adoption in
Raumfahrt as a strategic objective, not as a hypothesis to test.

## §3 Cross-cluster relationships

The deck's slide 7 sketch shows DaMaST's relation to neighbouring
projects. Verbatim labels: *LCL · 2025 · DLR Raumfahrtinstitute · PNA ·
PQS · MFT · FAS · FK · AH · Data Management for Space Transport · 2026 ·
FL · RDM · DaMaST*. The labels are abbreviations the deck does not
expand; for thesis-substrate purposes the interpretation is held
pending primary-source confirmation. (The naming pattern matches an
internal DLR LCL — *Leitcluster* — cross-cutting work-package shape.)

## §4 Honest gaps

- **Author scope is unclear.** Slide 4–6 use the first-person plural
  ("Vorhaben") consistently; the deck names no co-authors. Krebs is
  named on slide 1 as presenter, and the file's `dc:creator` field
  identifies Krebs as authoring/modifier — but this does not establish
  that Krebs is the project lead, only that this is his briefing of it.
- **STORT and LUMEN as use-cases.** The deck names them but the deck
  does not document the technical fit between Shepard and either
  use-case's data shape. The LUMEN namesake is already a fork-internal
  showcase synthetic dataset; whether the DaMaST instantiation will
  use the same synthetic shape, ingest real LUMEN test data, or build
  something disjoint is not stated.
- **Governance failure mode.** Slide 4 explicitly attributes the 2025
  proposal failure to *governance* concerns. For thesis evaluation
  this matters: the same federation work that motivates HMC Phase 2 and
  NFDI4Ing has governance dimensions that have not been solved at the
  DLR-internal-cluster level either. This is a real research-policy
  finding, not a slide-deck artefact.
- **No bridge yet between DaMaST and the BT/ZLP rollout
  (`aidocs/strategy/100`).** Slide 6 names *Sammlung von Erfahrungen
  aus anderen Sektoren* as a stated benefit ("collecting learnings
  from other sectors") — meaning DaMaST treats the BT/ZLP rollout as a
  reference instance whose lessons feed forward. The reverse direction
  (Shepard learnings from Raumfahrt feeding back to BT/ZLP) is not in
  the deck.

## §5 Thesis mapping

- **§1 Introduction** — second concrete deployment instance for the
  *shepard + databus + MOSS* stack outside ZLP; supports the
  cross-cluster federation argument.
- **§3 Architecture** — same DataHub triple as HMC/NFDI4Ing — single
  substrate stack across funding lines.
- **§6 Case study** — Raumfahrt is a sibling demonstrator to MFFD
  (BT/ZLP) and PLUTO (Bremen); thesis §6 carries all three.
- **§9 Discussion** — governance as a recurring failure mode across
  federation proposals (DaMaST 2025 proposal failure as a primary-
  source data point).

## §6 References

Primary source: [@krebsDamastIntro2025]. Companions:
[@hmcPhase2WpKrebs2025], [@nfdi4ingIntroKrebs2025],
[@welzmuellerPlutoPoster2025].
