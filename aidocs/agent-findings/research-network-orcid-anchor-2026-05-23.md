---
title: Research-network ORCID anchor + cross-platform sweep — findings
stage: feature-defined
last-stage-change: 2026-05-23
audience: thesis-substrate, agent-findings
---

# Findings — ORCID anchor + general platform sweep for Florian Krebs (DLR ZLP Augsburg)

**Date:** 2026-05-23 (fourth pass on the research-network task)
**Scope:** Course-correction extension to in-flight `aidocs/strategy/103-research-network.md` + `aidocs/strategy/104-author-research-profile.md` work. Per user instruction: *"orcid is primary anchor"* + *"also do a general websearch on me on platforms and scientific databases"*. Definitive ORCID identifier resolved + cross-platform presence mapped.

## What I found

### The definitive anchor

- **ORCID:** **`0000-0001-6033-801X`** — full name on record is **Florian Benedikt Krebs** (middle name was not in our internal records before this pass)
- Public API endpoint: `https://pub.orcid.org/v3.0/0000-0001-6033-801X/record` (retrieved 2026-05-23, 28 distinct works listed)
- Country: DE
- External identifier on ORCID: Scopus Author ID `16022558700`

### New biographical facts surfaced via ORCID

1. **Education dated precisely**: Universität Augsburg, **Diplominformatiker (Applied Computer Science), 2002–2008**. Previously the docs said "Diplom-Informatiker" without the date range.
2. **Pre-DLR industrial year**: **KUKA Roboter GmbH, Augsburg — April 2008 to September 2009** (17 months). Not previously in any of our docs. This is structurally significant: KUKA is the same vendor whose KUKA-KR-series robots later anchor every MFZ / FPC / T-AFP cell at ZLP. The clean industrial-pedigree → research-substrate arc runs through KUKA Augsburg, not directly from university into DLR.
3. **DLR employment start**: October 2009 (per ORCID employment record). Current role on ORCID: "Leader of flexible automation systems group". On LinkedIn: "Deputy Head of Department".
4. **2008 IEEE SMC paper** *"Relational Cognitive Structures for Intelligent Agent and Robot Control"* (DOI 10.1109/ICSMC.2008.4811781) — earliest publication on ORCID, predates DLR employment by ~1 year. **The deep-prior ontology-first signal** arising from Krebs's Diplom-thesis era; 18 years before the same posture is articulated in the Shepard fork's ontology-driven design.

### New publication clusters surfaced

The full list of 14 new ORCID-attested peer-reviewed publications was added to `docs/_data/references.bib` by a sibling agent invocation (see the "ORCID-anchored Krebs publications added 2026-05-23" block, ~lines 1554–1798). They span 2008–2024 and expand the peer-reviewed track from 3 (the docs' prior count) to **17 distinct peer-reviewed Krebs publications**.

The **single highest-leverage finding**: **`@krebsFairDigitalObjectsHelmholtz2022`** (DOI 10.3897/rio.8.e94758). This is *"Realizing FAIR Digital Objects for the German Helmholtz Association of Research Centres"* in *Research Ideas and Outcomes* (RIO 2022), with Krebs as 6th-named author on a 10-author paper led by **Thomas Jejkal (KIT-SCC, HMC PID-services lead)**. The full canonical author list, verified against `https://riojournal.com/article/94758/list/authors/`: Jejkal T, Pfeil A, Schweikert J, Pirogov A, Barranco PV, Krebs F, Koch C, Guenther G, Curdt C, Weinelt M.

**Why this matters:** Before this pass, the docs framed Krebs's HMC relationship as *strategic-only* (via Wiestler, Zachgo, the BMFTR PoF V framework letter — Cluster F in `aidocs/strategy/103` taxonomy). The corrected reading after the FAIR-DO 2022 paper surfaces is **both strategic AND technical** — Krebs has been a peer-reviewed co-author in the HMC PID + FAIR-DO working group since 2022. The HMC Phase 2 work-package commitments (`@hmcPhase2WpKrebs2025` WP-1 export/import + PID, WP-2 semantic-features, WP-3 cross-DLR DM interoperability) therefore stand on a four-year pre-existing working relationship with the same KIT-SCC group whose PID-services define the Helmholtz substrate. This was added to `aidocs/strategy/103` as a new **Cluster J** spanning §2 (table), §3 (per-person rows), §4 (Cluster J narrative), §5 (mermaid graph: Jejkal node + solid Krebs↔Jejkal edge), §6 (publications table).

### Cross-platform presence map

| Platform | URL / identifier | Status |
|---|---|---|
| **ORCID** | [`0000-0001-6033-801X`](https://orcid.org/0000-0001-6033-801X) | ✓ verified anchor (28 works) |
| **Scopus** | Author ID `16022558700` | ✓ (asserted by Scopus-Elsevier 2018-11-09 per ORCID external IDs) |
| **IEEE Xplore** | Author ID `37085504634` | ✓ stable URL (WebFetch returned HTTP 418 but URL pattern verified) |
| **GitHub** | [`noheton`](https://github.com/noheton) | ✓ profile confirms real-name "Florian Krebs" + ORCID linkback. 9 public repos including `shepard` (Java) and `f-ai-r` (TeX). |
| **LinkedIn** | [`florian-krebs`](https://www.linkedin.com/in/florian-krebs/) | ✓ headline "Deputy Head of Department, DLR" |
| **ResearchGate** | [`Florian-Krebs-75868717`](https://www.researchgate.net/scientific-contributions/Florian-Krebs-75868717) | ✓ exists but **unclaimed contribution stub** |
| **Google Scholar** | (no personal-profile page) | ✗ no profile (only search hits) |
| **DBLP** | (no matches) | ✗ unindexed despite multiple IEEE-conference papers |
| **CORDIS** (EU) | — | ✗ no PI records |
| **Gepris** (DFG) | — | ✗ no PI records |
| **re3data** | — | ✗ no dataset registry entries (and: Shepard itself not registered) |
| **Email** | `florian.krebs@dlr.de` | ✓ canonical (on `@krebsDlrk2021`) |

## Opportunities

1. **DBLP self-claim** — ~5 minutes. Krebs has IROS 2017, CASE 2018, ETFA 2015, ICRA 2024 papers that should be DBLP-indexed. With ORCID as the anchor, the claim flow is trivial. Unlocks discoverability through the standard CS-academic citation graph.
2. **Google Scholar profile** — ~10 minutes. With ORCID and IEEE-Xplore IDs already in hand, the Scholar profile claim is mechanical and surfaces citation counts the thesis defence will want.
3. **ResearchGate claim** — Krebs has a contribution stub but has not claimed it. Claiming would aggregate citations + allow direct correspondence from the ResearchGate side.
4. **Shepard on re3data** — register the Shepard fork as a research-data-management tool on re3data. This is a thesis-load-bearing visibility win.
5. **Cluster J author ORCID lookups** — 8 of the 9 new Cluster J individuals (Pfeil, Schweikert, Pirogov, Barranco, Koch, Günther, Curdt, Weinelt) need ORCID + affiliation lookups. A 15-minute pass through `helmholtz-metadaten.de` or the HMC Conference 2022 contributor list would close this.

## Ideas

- **A thesis-front-matter "online presence" block** is now buildable from §1 of 104. It would include: ORCID 0000-0001-6033-801X, Scopus 16022558700, GitHub noheton, LinkedIn florian-krebs, with the canonical contact `florian.krebs@dlr.de`. The avatar-photo gap in `feedback_avatar_reminder.md` is the only remaining item.
- **The KUKA Roboter year (2008–2009) is a defence asset, not a footnote**. The DLR-KUKA institutional relationship that produced the MFZ cells has a personal-continuity backbone through Krebs's pre-DLR year at the same vendor. Worth a sentence in the introduction chapter — *"this is a researcher who knows the hardware from the inside"*.
- **The 2008 IEEE SMC ontology paper is a thesis-rhetoric anchor**. The 18-year continuity from a Diplom-era ontology paper to the Shepard fork's ontology-first design philosophy is the cleanest single-evidence answer to *"did you invent this approach or inherit it?"* — Krebs inherited the ontology-first posture from his own 2008 self.

## Real-world impact

- The **Krebs ↔ Jejkal edge (FAIR-DO 2022)** changes the network's structural geometry. Before this pass, the HMC alignment was a dashed (strategic-only) edge through Cluster F. After this pass, it is a solid (co-authorship) edge through Cluster J. **This is the kind of finding that hits during a thesis defence Q&A** ("how does Shepard fit into the Helmholtz FAIR-DO landscape?") and now has a one-paper answer.
- The **publication-count delta** (ORCID 28 / bib ~40+ / eLib ~63) was previously invisible. After §0bis.3 in `aidocs/strategy/103`, the difference is explained: ORCID = peer-reviewed lower bound, bib = primary-source middle, eLib = institutional-footprint upper. A reviewer who spots the difference in any one document now finds the explanation in another.
- **DBLP, CORDIS, Gepris, re3data** all returned negative — that is itself a finding. The thesis story is *"DLR-employed researcher in HMC + NFDI4Ing partner roles"*, not *"independent PI on a DFG/EU grant"*. The negative results are consistent with the institutional-channel reading.

## Gaps & blockers

1. **ResearchGate is unclaimed** — low priority but visible-asymmetry vs. better-claimed profiles in the peer group.
2. **DBLP is unindexed** — see Opportunities #1.
3. **8 of 9 Cluster J co-authors lack ORCID + affiliation lookups** — flagged in `aidocs/strategy/103` §7.6.
4. **The Krebs → Voggenreiter / MFFD AFP edge is still dashed** — the ORCID pass did not surface a co-authored MFFD paper. The §8 thesis-vulnerability remains intact.
5. **Patrick Kaufmann was mistakenly listed as a FAIR-DO 2022 co-author** in an earlier draft of the bib entry (probable HMC-working-group roll confusion). Corrected 2026-05-23 to the canonical 10-author list; the surrounding 103 narrative is consistent.

## What surprised me

- **The KUKA Roboter year.** Sixteen years of internal context and the docs did not have this — Krebs went from Diplom-Informatiker (Univ. Augsburg) → 17 months at KUKA → DLR ZLP. The "vendor-side researcher returning to research with vendor knowledge" arc is a stronger industrial-pedigree story than "CS Diplom → directly into DLR".
- **The 2008 IEEE SMC paper.** "Relational Cognitive Structures for Intelligent Agent and Robot Control" — written during the Diplom era — is a structurally identical posture to Shepard's ontology-first design. The thesis story has a *much* deeper prior than the docs were assuming.
- **DBLP missed Krebs entirely.** With ICRA 2024, IROS 2017, CASE 2018, ETFA 2015 in the record, this should be a 5-record DBLP profile at minimum. The miss is real and fixable.
- **CORDIS + Gepris are empty.** I expected at least one PI-level entry given the HMC + NFDI4Ing alignment. The negative result clarifies that Krebs's funding posture is institutional (via DLR centre + Helmholtz programs) rather than personal-PI — which is consistent with the thesis story but is itself a finding worth knowing.
- **Two parallel agents (one for bib, one for §0) were working on this at the same time.** This is the first time I've seen three sibling-pass agents (bib, §0, §0bis) compose into a coherent multi-section document without explicit coordination. The structural integrity held because each agent extended rather than overwrote — a working model for multi-agent doc maintenance.

## Bibliography delta from this pass

- **Verified:** `@krebsFairDigitalObjectsHelmholtz2022` canonical author list corrected (was over-broad — included Patrick Kaufmann erroneously; now matches canonical 10-author RIO paper)
- **Cross-confirmed by another agent:** 14 ORCID-derived peer-reviewed entries spanning 2008–2024 were added by a sibling agent pass; this report and the docs that cite the entries are consistent.

## File touch list

- `aidocs/strategy/103-research-network.md` — added §0bis (cross-platform presence), Cluster J in §2/§3/§4/§5/§6, §7.5 (DBLP gap), §7.6 (Cluster J lookups), §11 changelog entry for the fourth pass.
- `aidocs/strategy/104-author-research-profile.md` — extended §1 at-a-glance (full name, KUKA year, IEEE/GitHub/LinkedIn/ResearchGate IDs), added 2002–2008 / 2008 SMC / 2008-04 KUKA / 2009-10 DLR rows to §2 timeline, added Cluster J paragraph to §3, demoted resolved §9 asks.
- `docs/_data/references.bib` — corrected the canonical author list on `@krebsFairDigitalObjectsHelmholtz2022` (was over-broad; now matches RIO paper); 14 other entries added by a sibling agent in the same session.
- `aidocs/agent-findings/research-network-orcid-anchor-2026-05-23.md` — this report.

## Honest verdict

The ORCID anchor + general platform sweep resolves the largest open gap in the research-network and author-profile docs: **the verified identity foundation**. The thesis substrate can now point to a single canonical identifier (ORCID 0000-0001-6033-801X) that resolves to a public, machine-readable record carrying employment, education, and 28 works. The cross-platform sweep further confirms the network exists on standard professional surfaces (LinkedIn, IEEE Xplore, GitHub, Scopus) while honestly recording where it does not (DBLP, Scholar profile, CORDIS, Gepris, re3data).

**The strongest single finding** — Krebs ↔ Jejkal HMC FAIR-DO 2022 co-authorship — turns a previously-dashed strategic-only edge into a solid technical-cohort edge, and is now positioned in `aidocs/strategy/103` Cluster J narrative + the bibliography as the load-bearing peer-reviewed attestation of Krebs's HMC working-group relationship. This is the kind of finding a defence committee can verify in 30 seconds via DOI and that materially strengthens the thesis-defence case for HMC Phase 2 WP-1 (export/import + PID infrastructure) being grounded in real prior collaboration.
