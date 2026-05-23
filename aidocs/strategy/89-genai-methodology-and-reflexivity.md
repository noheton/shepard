---
title: "Generative AI as research method — Krebs's stated position and this project's observed practice"
subtitle: "(thesis chapter draft — methodology and reflexivity)"
stage: feature-defined
last-stage-change: 2026-05-23
audience: [thesis, methodology, reflexivity, ethics-reviewer]
---

# Generative AI as research method — Krebs's stated position and this project's observed practice

*Thesis chapter draft. This is the **methodology and reflexivity**
chapter — the one that asks whether the author's stated position on
generative-AI use in scientific work aligns with the AI-collaborative
practice the thesis itself demonstrably uses. The chapter draws its
stated position from a 17-slide author-led tutorial uploaded to the
project's working memory on 2026-05-23
[@krebsGenKi2026] and compares that position against the observed
practice recorded in this project's collaboration-highlights memory,
the f(ai)²r provenance design, and the energy-and-cost-accounting log.*

## 1. The chapter's reflexive premise

Any thesis that uses AI assistance as part of its method has to make
the use legible. The OECD's 2024 *AI in Science* report frames this as
a transparency-of-process requirement; the EU AI Act's Article 50
operationalises it as a disclosure obligation. The framing this
chapter inherits is that *transparent AI-assisted production is not a
weakness of the thesis but a methodological contribution* — provided
the practice is reproducible, the failure modes are named, and the
author's posture is publicly stated independently of the convenience
of the moment.

Florian Krebs has, as it happens, given a public talk that states the
posture. The slide deck *Generative KI: Technologie, Ethik und
Zukunft — Wie LLMs (unser) Arbeiten und Denken (verändern)*
[@krebsGenKi2026] is a primary source for the author's position. The
deck closes with an explicit "AI Disclosure" slide, an unusual move in
DLR talks of this register. That disclosure slide is the methodological
hinge of this chapter.

## 2. The deck's seven-step exposition

The deck is structured as a tutorial for scientists not already
fluent in LLM mechanics. Its seven sections [@krebsGenKi2026] are:

1. **The core principle — probabilities not magic.** LLMs are
   prediction machines; they compute the statistical probability of
   the next token from the previous context [@vaswani2017attention].
   They do not "understand" in any human sense, and emergent
   capabilities (e.g. few-shot learning) are scale-driven, not
   intent-driven.
2. **Tokenisation — the atoms of language.** Byte-pair encoding;
   compound-rich languages (e.g. German) require more tokens; numbers
   are often split arbitrarily, which makes arithmetic structurally
   difficult.
3. **Vocabulary and IDs.** Each token gets a unique integer; the
   vocabulary is finite (50k–250k entries); out-of-vocabulary words
   inflate sequence length and cost.
4. **Embeddings as a vector space.** Each token is mapped to a long
   vector; closer vectors share semantic content; embeddings are the
   foundation for every downstream computation.
5. **The transformer engine — attention is all you need.** Standard
   attention costs *O(n²)* in sequence length; newer architectures
   (Mamba/SSMs) push toward *O(n)*.
6. **Three training phases.** Pre-training on trillions of tokens;
   supervised fine-tuning on human-curated instructions; alignment via
   RLHF for the Helpful–Honest–Harmless (HHH) objective.
7. **Scaling limits — the data bottleneck.** Chinchilla optimum
   roughly *20 training tokens per parameter*
   [@hoffmann2022chinchilla]; high-quality human text projected to
   exhaust between 2026 and 2032; model collapse follows when training
   loops back on synthetic outputs [@shumailov2024modelCollapse].

The deck then names the **alignment problem** in its plain form:
LLMs do not automatically align with human values, and the HHH frame
plus RLHF are partial mitigations rather than solutions. Misalignment
risks include unwanted behaviours, misinformation, and discrimination.

## 3. The four risks-for-scientists slide

Slide 10 of the deck names four specific risks for scientists working
with LLMs [@krebsGenKi2026]:

| Risk | Description | Consequence |
| --- | --- | --- |
| Hallucinations | Plausible-sounding but factually false information | Threat to scientific integrity |
| Bias & prejudice | Amplification of systemic biases from the training data | Discriminatory or distorted scientific results |
| Information security | Automated creation of malware or exploitation of vulnerabilities | Threat to research infrastructure |
| Reproducibility | Use of proprietary models makes results unverifiable | Decline of scientific credibility |

The reproducibility risk is the most thesis-relevant of the four. The
deck names it as the failure mode in which "use of proprietary models
makes results unverifiable." The thesis's f(ai)²r-based AI-provenance
substrate (`project_fair2r_integration.md` in working memory;
[`aidocs/semantics/`](../semantics/) for the implementing-side detail)
is the structural reply to precisely that risk. Every AI interaction
captured in the substrate carries the model name, the model version,
the prompt, the response, and the human-acceptance grade. Whether a
specific conclusion in the thesis is reproducible-without-rerunning-the-LLM
is then a query over the substrate, not a forensic exercise.

## 4. The responsibility-and-management slide

Slide 11 names the active-mitigation programme [@krebsGenKi2026]:

- **NIST AI RMF** [@nistAiRmf_2023] — the structural framework for
  steering AI risk.
- **Robust tests (red teaming)** — systematic search for failure
  modes.
- **Data provenance** — traceability of training data and labelling
  of synthetic content.
- **Human oversight** — the final decision and control authority
  remains with the human.

These four are the deck's prescription, and the thesis can adopt them
as the criteria by which its own AI-assisted practice is evaluated.
The first and the fourth (NIST AI RMF; human oversight) are
posture-level commitments. The second and the third (red teaming;
data provenance) are substrate-level requirements that Shepard either
delivers or does not.

## 5. The closing AI-disclosure slide — the deck's most thesis-relevant moment

The deck's final substantive slide (slide 17) is titled *"Über diese
Präsentation - AI Disclosure"* and reads, in full
[@krebsGenKi2026]:

> *Diese Präsentation wurde mit Hilfe künstlicher Intelligenz und
> agentischen Workflows erstellt.*
>
> **Verwendete KI-Tools:**
>
> - *Scalable Artificial Intelligence Accelerator (SAIA): LLMs und APIs
>   der GWDG Scientific Cloud (→ 100% DLR konform)*
> - *MCP Server: Papersearch, Docling-MCP-Server, Web-Suchtools,
>   Bildgenerierungs- und Bearbeitungstools für visuelle Elemente,
>   Dokumentations- und Workflow-Management-Tools*
>
> *Ansatz ist übertragbar auf Paper, Berichte und Code schreiben. Bei
> Interesse gerne detaillierte Vorstellung.*

The slide also carries a small workflow diagram: *Decide on Topic →
Research Source Documents → Convert Documents to Markdown → Write /
Iterate Presentation → Export to PDF, PPTX.* A preceding slide (16)
shows a screenshot of the author's working environment — a VS Code
window with the Marp CLI presentation source, an MCP-enabled assistant
panel on the right, and the assets folder visible — establishing the
disclosure as *practised*, not merely *declared*.

Two things are doing work here for the thesis:

- The slide names a **specific, DLR-compliant cloud** (SAIA / GWDG) as
  the LLM substrate. This is the institutional rebuttal to the
  reproducibility-risk slide: the author is not running on a black-box
  consumer service.
- The slide names **MCP servers** — specifically Papersearch, Docling,
  web-search, image-tools, documentation-and-workflow-management tools
  — as the agentic-workflow stack. This is the same family of tools
  this project's working memory shows in active use (the project's
  MCP set includes paper-search, Context7, Microsoft Learn, fetch,
  pg-aiguide, the Consensus search MCP, and several others).

## 6. Stated position versus observed practice — the reflexivity audit

The chapter's reflexive question is whether the author's stated
position aligns with the observed practice of the project that hosts
the thesis. The honest finding is **alignment, not divergence**, in
six respects:

1. *Transparent disclosure.* Krebs's deck includes an explicit
   AI-disclosure slide. This project's commit log, `aidocs/16` backlog
   discipline, and `aidocs/sustainability/00-energy-estimation-log.md`
   provide commit-level AI disclosure — every commit can be traced to
   the model that helped author it, and the energy footprint of that
   help is published.
2. *Choice of substrate.* Krebs's deck names a DLR-compliant cloud as
   the LLM substrate; the project's working memory
   (`project_ai_plugin_config.md`) names SAIA/GWDG as the recommended
   provider for the DLR deployment. The two are the same choice.
3. *Reproducibility.* Krebs's deck names reproducibility as a
   distinctive risk of proprietary-model use; the project's
   f(ai)²r-based AI-provenance design is the substrate-level response
   to that risk. The MEMORY entry on f(ai)²r records the design as
   `ai-provenance-vocabulary in primary use`.
4. *Human oversight.* Krebs's deck names human oversight as the final
   authority; the project's `feedback_agents_argue_and_consult.md` and
   `feedback_agent_clarify_first.md` rules require AI agents to defer
   to the human at named decision boundaries. The project's
   persona-board pattern is the structural form of that deferral.
5. *Data provenance.* Krebs's deck names data provenance as a
   mitigation requirement; the project's snapshot-boundary rule, its
   "dataset forging" framing (`project_dataset_forging.md`), and its
   per-payload-kind capture of upload+human-curation+AI-suggestion
   activities form the substrate-level realisation.
6. *Workflow shape.* Krebs's deck shows a *Decide → Research → Convert
   → Write/Iterate → Export* workflow. This project's working
   discipline follows the same shape — orient → research → distill →
   draft → land in the same PR — and the *Research Source Documents*
   step is, in fact, the present chapter's own genesis.

The reflexivity audit therefore returns no divergence finding. The
stated and observed positions converge on the same six commitments.

## 7. What this means for the thesis methodology chapter

The thesis methodology chapter can be unusually direct, because the
reflexivity audit closes cleanly:

- The thesis was produced under a stated and documented AI-assisted
  practice. The practice is consistent with the author's previously
  stated and publicly delivered position on AI in science.
- The AI substrate used (SAIA/GWDG; Anthropic Claude via Claude Code;
  enumerated MCP servers) is named, reproducible at the institutional
  level, and within the DLR-compliance envelope.
- The reproducibility-risk mitigation is *substrate-level*: AI
  interactions on this project are captured as f(ai)²r-typed activities
  whose model name, version, prompt, response, and acceptance grade
  are queryable as data, not as anecdote.
- The energy and carbon cost of the AI-assisted practice is published
  in [`aidocs/sustainability/00-energy-estimation-log.md`](../sustainability/00-energy-estimation-log.md)
  alongside the substantive contributions, with a stable methodology
  and a continuous backfill discipline.
- Human oversight is preserved by the persona-audit pattern, the
  clarify-first agent protocol, and the explicit ask-before-acting
  boundaries on autonomous-mode operation.

The methodology chapter, in this framing, is not a defence of the
thesis. It is a contribution: a worked example of how a research
project can use generative AI in the open without ceasing to be
scientific.

## 8. Where the deck does **not** answer the thesis's question

The deck has limits the thesis should not paper over:

- *The deck does not name a specific model.* The thesis can name the
  models (Claude Opus 4.6, Opus 4.7, etc.) that produced its
  AI-assisted material; the deck stays at the substrate level
  (SAIA/GWDG, MCP). This is fine — the deck's audience is broader —
  but the thesis owes the more specific disclosure.
- *The deck does not name costs.* The deck does not address the energy
  or financial cost of LLM use. The thesis does, in
  [`aidocs/sustainability/00`](../sustainability/00-energy-estimation-log.md).
- *The deck does not name failure-case material.* The thesis can — the
  `project_collab_highlights.md` log includes "when I was wrong"
  entries; the methodology chapter inherits them.

These are extensions the thesis methodology chapter has the room to
make and the source material to support.

## 8.5 A biographical aside on the author's posture

A reflexivity chapter that names *stated position vs observed
practice* (§6) and *failure-case material* (§8) can afford a third,
lighter, primary-source register: the author's own self-framing,
preserved in a single-page satirical "Wanted" poster Krebs kept on
his office door at DLR ZLP, authored 2012-07-25
[@wantedFlo2012]. The text is brief and deliberately ridiculous —

> *"WANTED. EVIL GENIUS seeks minions to sacrifice their lives in
> world domination attempt. Must be prepared to work 24-7 for
> psychopath for close to no pay. Messy death inevitable but costumes
> and laser death rays provided. NO Weirdos."*

The poster is not a methodological document. Its value to the
reflexivity chapter is twofold and limited. First, it dates an
author-stance that is *self-aware about the asymmetry between PI and
collaborator* — the satire works because the underlying labour
relation it parodies is real — fourteen years before the same author
articulated, on a public deck, a thoughtful position on the
human-machine asymmetry in AI-assisted science
[@krebsGenKi2026 ; slide 14]. Second, it is a small primary-source
calibration on the *register* of the author's working voice: the
project's MEMORY entries on highlight-capture, on the persona-board's
willingness to argue back, on the "Reluctant Senior Researcher"
critic role being an in-house critic rather than an external one —
all of those design choices are consistent with an author who, in
2012, found the *evil-genius-and-minions* framing of academic
hierarchy worth nailing to the office door as a joke against
himself.

The reflexivity chapter does not need to make more of this than
that. The poster is included as a footnote-class primary source —
not load-bearing, but on the record, and consistent with the
*stated and observed practice converge* finding of §6.

## 9. Closing — the chapter the deck makes possible

The thesis's methodology chapter has, in this deck, the rarest of
methodological resources: a public, primary-source-attested statement
of the author's posture, made for an institutional audience,
independent of the convenience of the present argument. Citing it is
not self-citation. It is verification that the methodology chapter is
describing a posture the author has already publicly defended.

## Primary sources

@krebsGenKi2026 — *Generative KI: Technologie, Ethik und Zukunft —
Wie LLMs (unser) Arbeiten und Denken (verändern)* (PDF, 17 slides;
authored by Florian Krebs, DLR BT-ZAP; creation date 2026-02-26).
Uploaded to the AI working memory of this project on 2026-05-23.
The deck's own bibliography (slide 14) cites @vaswani2017attention,
@hoffmann2022chinchilla, @shumailov2024modelCollapse, and
@nistAiRmf_2023 among 19 references; the relevant subset has been
copied into this project's bibliography for downstream citation.

@wantedFlo2012 — *"Wanted" office-door poster* (DOC, 2 pp., 35 words;
authored by Florian Krebs, DLR ZLP Augsburg; create-date 2012-07-25,
last-saved 2012-07-25 10:41). Single-page satirical "Wanted" notice
that hung on the author's office door. Primary source for §8.5 only;
not load-bearing. Uploaded to AI working memory 2026-05-23.

Citations resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib).
