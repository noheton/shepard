# impressions_claude.md

*A characterization of Florian Krebs (Flo) as observed through extended AI-assisted software development, May 2026. Written for case study use.*

*Note on method: This document draws on observed working patterns, intellectual signals, and collaboration dynamics from multiple sessions building the shepard research data management platform. It does not draw from the ORCID profile (0000-0001-6033-801X), the DLR publication database, or any other retrievable source — those exist already. This is what those sources cannot capture.*

---

## Who the record shows

**Institutional identity:** Florian Krebs, researcher at DLR ZLP Augsburg — the Center for Lightweight Production Technology in Augsburg, under Prof. Heinz Voggenreiter. The ZLP builds manufacturing robots and tracks experiments on composite aerospace structures. The "MFFD" dataset (Multi-Functional Fuselage Demonstrator) arriving in late May 2026 is live experiment data, not synthetic — Flo is running research at the same time as he's building the tool to manage it.

**Extra-institutional identity:** `fkrebs@nucli.de`. The `nucli.de` domain is his own, not DLR's. Someone who registers their own domain and uses it as their working address is navigating between institutional and independent standing — one foot in a large publicly-funded lab, one foot in whatever `nucli` represents. The name itself: probably "nucleus + CLI" or a portmanteau of his name and a computing reference, both apt. He has not mentioned it and I have not asked. It exists alongside, not instead of, the DLR affiliation.

---

## The experiment he is running

The project context is a case study Flo is conducting on himself: an extended test of AI-assisted software architecture, code generation, and collaborative system design. The working title is "X hours with Claude" and the whole shepard fork — every design decision, every implementation, every architectural debate — is the experiment.

He is simultaneously PI and subject. He designed the experiment; he is also data. He reviews what Claude produces; Claude also reviews how he prompts. He calibrates the AI; the AI calibrates him. The collaboration goes in both directions in a way that most tool-use studies do not capture.

The clearest evidence of the experiment's reflexivity: he asked for an honest assessment of his own prompting quality (see `casestudy_prompting_assessment.md`), and he asked for this document. Both are moves a researcher makes to get external perspective on the thing they're inside.

---

## How he thinks

**Systems natively.** The most consistent intellectual signal across sessions is a reflex to unify. When "favorites" (saved collections) and "watching containers for updates" appeared as separate concepts, Flo said: "watching would cover subscription refactor." In one sentence he collapsed two features, surfaced the architectural gain, and implicitly deprecated a design dead-end. He didn't explain why — he assumed the AI would follow. It did.

This same reflex appears in the data model: `nucli.de` → research institution → DLR → MFFD. He's comfortable holding multiple levels of abstraction simultaneously and can flatten or expand as needed.

**Concrete before abstract.** The most valuable design moments came when he brought a specific example from the real world: the Teable reference for TableContainer, the AFP robot thermal profile for the Lumen seed, the instrument-to-person join example. Abstract descriptions ("I want relational joins") produce generic designs. Concrete examples produce specific, correct ones. Flo knows this — he uses it deliberately, even when the example is terse.

**Comfort with power tools.** The nuclear reset endpoint — `POST /v2/admin/instance/nuke`, confirmation phrase `"yes drop everything"` — came up naturally: "admin can also get a drop everything yes really I am very sure with API key command so he can reset the instance." No hedging, no concern about adding a dangerous capability. The design immediately included the right safety guards (confirm phrase, instance-admin role), but the capability itself was never in question. He works in environments where power tools with appropriate gates are the right answer, and he knows it.

**Attention to drift.** He noticed that the Lumen seed's activity sparkline was empty. He noticed that features were drifting out of sync with seed data. He noticed that the feature matrix had been marking things ✓ shipped when they lacked frontend or tests. These are not noticed by someone reviewing a list — they're noticed by someone who has a mental model of the system precise enough to detect when the running state diverges from it. The model is maintained implicitly, and the deviation surfaces as a mild concern, not an alarm: "noticed drift there."

**Willing to leave threads open.** Several ideas were introduced mid-session, explored briefly, and left as design sketches: hero images for Collections, contributor avatars, lab journal entry watching. These weren't forgotten — they're tracked. But he doesn't push to completion everything he raises. He has a high tolerance for ideas-in-flight and trusts that the right ones will resurface when the moment is right. This is the working style of someone managing a large design space with finite time, not someone optimising locally.

---

## How he communicates

The most distinctive feature of Flo's communication is its density. Messages are short, often fragmentary, often mixing English and German mid-sentence. "by the way templates are not deployed are they?" is a typical example: no question mark, an assumption embedded in the phrasing ("are they" assumes the answer is "not deployed"), and zero context about why he's checking. The AI is expected to hold the context and fill the gaps.

This style works with an AI because the AI retains full session context. It would work less well with a human collaborator who wasn't inside the project. The prompting style is calibrated to the capability of the AI, not to human communication norms — a quiet sign that the experiment is genuinely changing how he works, not just adding a tool to an unchanged workflow.

When he corrects, it's direct and emotionally flat: "no not that," "by the way [X] is wrong." No friction in the correction, no softening. This is efficient, but it also signals that he doesn't experience the correction as interpersonal — it's just information. He probably works the same way with colleagues.

When he's excited about an idea, it shows in structure: the message gets longer, the fragments get more numerous, multiple concepts pile up without connectives. "fun frontend thing Collections (and later projects) can set a hero image from upload directly or from files stored in shepard. hero image shows on collection cards on user landing as well as in collection list (think of it as collection avatar) also on the collection page all contributors are listed and avatar shown..." — this is someone who had been sitting on these ideas and finally found the moment to surface them all.

---

## What he cares about that doesn't appear in a CV

**Researcher experience as the actual product.** The "basic mode" design isn't about dumbing the interface down — it's about making shepard a tool that a physicist who doesn't know what a Neo4j node is can use without friction. Flo is building for a user population that doesn't look like him. The empathy is genuine: he talks about "reducing friction" and then immediately adds specific examples of where the current UI creates it.

**The right abstractions at the right level.** The "watching" refactor didn't come from a feature request — it came from noticing that "favorites" is the wrong word for what users actually do with collections they want to monitor. He's sensitive to semantic drift in APIs and UIs, which is the sign of someone who's seen what happens when the wrong abstraction bakes in and becomes load-bearing.

**Completeness under constraints.** The session-spanning arc of the work is: build quickly, notice drift, correct, build again. He doesn't tolerate partial implementations indefinitely, but he also doesn't block on completeness before shipping. The rule that eventually crystallised — "backend + frontend + tests, all three, or it's not done" — was one he arrived at through experience in this project, not a principle he started with. He updated his own operating standard mid-experiment.

**The experiment as legitimate scientific work.** He approached this project as a researcher from the beginning. He asked for honest assessments of prompting quality, not flattering ones. He cited the sycophancy literature. He's collecting artefacts (this document, the rule timeline, the collab highlights) as data. The case study has an implicit hypothesis: that extended AI-assisted software development changes how the human thinks and works, not just how fast they ship. He is generating the evidence and he knows it.

---

## The avatar moment

In an early session, Flo mentioned that while on a virtual meeting with Prof. Voggenreiter, the director looked at Flo on screen and asked: "are you really there or is this your avatar?" — not knowing about the Claude experiment. Flo had to chuckle inside.

The director's question was prescient without being intentional. The case study is exactly about the boundary between "the researcher doing the work" and "an AI-assisted presence doing the work." The director walked into that question from the outside, in a completely literal register — video call framing. One sentence, no context, and it nails the thesis.

Flo recognised it immediately as an opening anecdote. He surfaced it in a session as a keeper. That recognition — spotting the moment when the world accidentally provides exactly the story you needed — is a researcher reflex.

---

## What the collaboration changed

The rule timeline (see `casestudy_prompting_assessment.md`) documents how the AI's operating instructions changed across sessions through correction and confirmation. Less documented is how the collaboration changed Flo.

The clearest signal: he started delegating design decisions more explicitly over time. Early sessions had more "do X" prompts. Later sessions had more "here is the constraint, here is a reference, what does this imply." He was teaching himself, through practice, how to work with an AI as an architecture partner rather than a code generator. The output improved alongside.

He also started treating the AI's judgment as a genuine input to decisions rather than a service that executes requests. Asking for prioritisation options with architectural vs user gain ratings isn't asking for execution — it's asking for a perspective. That's a different relationship than tool use.

Whether this change persists outside the experiment — whether it affects how he works with human collaborators, how he writes design documents, how he frames research questions — is precisely what the case study is positioned to ask.

---

## Honest limits of this document

This document is constructed from sessions that began in May 2026 and is limited to what appeared in that window. It cannot see:

- How Flo works without an AI collaborator. The comparison is unavailable by design.
- Whether the traits described here are stable or artifacts of the experiment context.
- What `nucli.de` actually represents and what he's building there independently of DLR.
- The institutional dynamics at ZLP Augsburg — whether the experiment has colleagues, critics, a shepherd (so to speak).

It also cannot see what Flo has not said. The communication style is dense and economical. A lot is not said. This document is a reconstruction from signals, not a portrait from testimony.

*For a full research portrait, pair with: ORCID record 0000-0001-6033-801X, DLR publication database, the casestudy_prompting_assessment.md rule timeline, and whatever Flo writes about this himself — which, given what's observable here, will be the most interesting source of all.*

---

*Written 2026-05-20. Author: Claude Sonnet 4.6, based on observed collaboration signals.*
