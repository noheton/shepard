---
title: "HoKiTeP — ZLP Augsburg extension (2022) and joint use-case orchestration (2025)"
stage: fragment
last-stage-change: 2026-05-23
audience: [thesis, strategy, contributor]
---

# HoKiTeP — ZLP extension and joint use-case orchestration

*Strategy doc anchored on two primary sources spanning the four-year arc
from a Bavarian-funded ZLP building extension to a multi-project ZLP-Halle-43
joint use-case orchestration. The two artefacts share a name (HoKiTeP) but
sit at different layers: 2022 is **a cell type + its housing**; 2025 is
**a project running in that housing alongside ~10 sibling projects**.
Conflating them loses both the €8 M Bavarian funding anchor and the
multi-project orchestration shape.*

## §1 HoKiTeP-the-cell (2022 — Bavarian-funded ZLP extension)

**Primary source:** anonymous-author DLR-institutional deck *Erweiterungsszenario
ZLP Augsburg — KI-Produktionsnetzwerk / HoKiTeP*, PDF created 2022-07-12,
5 slides [@hokitepPresentation2022]. The deck has no named author in PDF
metadata; content is institutional ZLP Augsburg.

**What HoKiTeP-the-cell is (slide 3):**

- *HochKinematisierte Technologie Entwicklungs-Plattform* — a new cell
  combining **industrial robots on linear axes** with **mobile units**;
  products are processed *im Team* by autonomous, intelligently networked
  mobile units cooperating with classical industrial robots.
- Stated design driver: maximum flexibility for *Lot-Size 1* and *Changeover*
  (very small batches / radically heterogeneous parts).
- Stated chaining capability: the cell links into existing robot cells
  (*Verkettung mit bestehenden Roboteranlagen*) for multi-step processes.

**Funding context (slide 2):** the extension sits inside the Bavarian
regional *KI-Produktionsnetzwerk* initiative, with **~€8 M from the Freistaat
Bayern**. Scope beyond HoKiTeP: a Co-Working-Space + extended lab/infrastructure
area (Fügelabor — joining lab; Werkstätten — workshops; Lagerflächen —
storage). The deck is the early-phase argumentation for a *GI Antrag*
(*Großgeräte-Investitionsantrag* — large-equipment investment proposal).

**Strategic context (slide 4):** the deck threads HoKiTeP into seven
strategy layers: DLR-Strategie 2030 + Luftfahrt-Leitkonzepte + HGF POF-4
Produktionstechnologie; Bayerische Luftfahrtstrategie 2030; BDLI
Technologiestrategie 2020 + BMWi Plattform Industrie 4.0 Leitbild 2030;
EU Clean Aviation SRIA + EASA Artificial Intelligence Roadmap; plus
KI-Strategie Bundesregierung 2020 and ESA Technology Strategy 2019.
Two technology spines converge on *Selbst-optimierende, Flexible
Produktionstechnologien*: **mobile systems → (Echtzeit-)Rekonfigurierbarkeit**
and **KI-Methoden → autonome Prozessführung**.

**Use-case sketches (slide 5):** four future application scenarios —
*Montage und Vorrüstung von Panels*; *Montage von Rumpftonnen* (fuselage-
barrel assembly); *Integration von Kabinenelementen*; *Flexible Montage
von Flugtaxis* (flying-taxi assembly).

This is the **physical infrastructure layer** of the HoKiTeP arc.

## §2 HoKiTeP-the-project — 2025 ZLP-joint-use-case orchestration

**Primary source:** the Krebs-authored HoKiTeP × ZLP joint use-case deck (DLR-internal primary source),
6 slides, modified 2025-12-05 [@hokitepZlpUseCase2025].

The 2025 deck is a different artefact in shape: it documents a **joint
use-case** running across the ZLP-Augsburg Halle 43 production hall, with
HoKiTeP referenced as one **project (HAP 2)** among ~10 named project
funding lines feeding the same use-case.

**The use-case (slide 2):** sequential CFRP-skin manufacture →
QS (quality assurance) → Stringer/Spante (frame parts, partly *Zukauf* —
purchased; partly 3D-printed, BT-ST?) → NDT → Assembly & System-Installation →
QS → final NDT. The target part is a *vorausgerüstetes Demo-Schale* (pre-
equipped demonstrator panel) — deliberately small to enable multiple
iterations. Focus areas: **process chaining** (à la Protec NSR, across
multiple cells), **digital end-to-end (digitale Durchgängigkeit)**, and
**geometric precision** (*"No Shim"* — no shimming required).

**The project zoo (slides 2-3):** the named projects orchestrated under
this joint use-case include — verbatim from the slide labels — ZLP,
NFDI4Ing 2, HMC 2, ASPIRO, **HOKITEP HAP 2**, HEMERA, HERA, INTEGRA,
DICADEMORE, ODIX/ForInfPro, GREATER, FRAME H2, HERFUSE, DaMaST.

That is **fourteen named project / funding lines** converging on a single
joint use-case in Halle 43. Several are already documented in the thesis
library: NFDI4Ing 2 (`aidocs/strategy/88`); HMC 2 (`aidocs/strategy/90`);
ODIX/ForInfPro (`aidocs/strategy/91`); DaMaST (`aidocs/strategy/95`).
*HOKITEP HAP 2* is the named HoKiTeP work-package inside the 2025 joint
use-case — a successor stream to the 2022 Bavarian extension proposal.

**Process structure (slide 3):** the deck maps each manufacturing step
to the projects funding it. Examples: Skin manufacturing (MB, P&P + QS +
VAP + PEI + QS + NDT) — assigned to HOKITEP + HERFUSE + FRAME H2;
Spante TP-AFP + PEI + QS + Presse + QS + NDT — FRAME H2 + HOKITEP;
Assembly CUW + QS + NDT — HOKITEP + FRAME H2 + HERFUSE; Calibration &
overall measurement — HOKITEP; **Datenmanagement & Analytics — NFDI4Ing 2,
HMC 2, ODIX/ForInfPro, DaMaST**.

That last assignment is load-bearing for this library: **the four
projects this thesis-substrate library has already integrated are
precisely the ones the 2025 joint use-case names as the data-management
& analytics layer for HoKiTeP HAP 2**. The Shepard-bearing project set
is treated by the orchestrator as a coherent unit, not as competing
funding lines.

## §3 Why this matters for the thesis

- **§1 Introduction** — HoKiTeP HAP 2 is a concrete substrate-consumer
  use-case for the federation-leaning architecture (Shepard + Databus +
  MOSS) that NFDI4Ing 2, HMC 2, ODIX, DaMaST jointly underwrite.
- **§3 Architecture** — the joint use-case demonstrates the substrate
  shape across multiple ZLP cells (Halle 43-scale process chain), beyond
  the single-cell (FPZ AFP) scope of MFFD.
- **§6 Case study** — HoKiTeP HAP 2 is a candidate evaluation site for
  Shepard's plugin-first / federation-ready architecture; the 14-project
  orchestration is the realistic complexity scale.
- **§9 Discussion** — institutional shape: 14 funding lines converging
  on one joint use-case is the empirical answer to "how does a research
  data infrastructure get funded?" — through bundles, not single grants.

## §4 Honest gaps

- The 2022 deck has no named author in PDF metadata; institutional
  authorship treated as ZLP-Augsburg-collective. The presenting-author
  identity is not in the record.
- The 2025 deck's slides 4–6 (*Balkenplan / Zulieferungen Projekte* —
  Gantt chart / project deliveries) are present in the file but contain
  almost no extracted text; the slides are likely image-based or the
  text was not in the standard text-run XML. The project-scheduling
  data is in the artefact but not in this distillation.
- Some abbreviations on the slide-2 project list (ASPIRO, HEMERA, HERA,
  INTEGRA, DICADEMORE, GREATER, FRAME H2, HERFUSE) are not yet
  cross-referenced in the library. They surface here as known project
  lines worth tracking when later artefacts arrive.

## §5 References

Primary sources: [@hokitepPresentation2022], [@hokitepZlpUseCase2025].
Companion library: [@nfdi4ingIntroKrebs2025], [@hmcPhase2WpKrebs2025],
[@krebsForInfPro2026], [@krebsDamastIntro2025].
