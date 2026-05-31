---
layout: default
title: Sustainability — energy and CO₂ disclosure
audience: admin
permalink: /admin/sustainability/
---

# Sustainability — energy and CO₂ disclosure

This page is for operators who need to report on the environmental footprint
of a Shepard deployment — for example, when filling a Data Management Plan (DMP)
sustainability section for an EU Horizon Europe or Clean Aviation JU proposal,
or for Helmholtz Association internal sustainability reporting.

---

## Overview

Shepard's development is AI-assisted. Every agent-driven commit consumes LLM
inference energy on Anthropic's cloud infrastructure, plus local build energy
and CI runner energy. These are tracked per-commit in an append-only energy log.

For operators: **production-runtime energy depends entirely on your infrastructure
choice.** Shepard makes no claims about the footprint of your deployment — only
about the development-side footprint of the software itself.

---

## What Shepard reports

**Development-side footprint (managed by the Shepard team):**

| Stream | Region | Carbon intensity | Source |
|---|---|---|---|
| LLM inference | AWS us-east-1 (conservative baseline) | ~290 gCO₂eq/kWh | EPA eGRID 2023 / Electricity Maps 2024 |
| Local build | DE grid | ~380 gCO₂eq/kWh | Umweltbundesamt Strommix 2024 |
| CI runners | Azure East US | ~310 gCO₂eq/kWh | Electricity Maps 2024 |
| Reference prod | Hetzner Falkenstein, DE | ~380 gCO₂eq/kWh | Electricity Maps zone `DE` |

The full per-commit ledger lives at
[`aidocs/sustainability/00-energy-estimation-log.md`](https://github.com/noheton/shepard/blob/main/aidocs/sustainability/00-energy-estimation-log.md).
The methodology (regional intensities, heuristics, confidence taxonomy) is at
[`aidocs/sustainability/01-methodology.md`](https://github.com/noheton/shepard/blob/main/aidocs/sustainability/01-methodology.md).

**Headline figures (backfilled development history, 214 commits):**

- Total estimated development energy: **~5.4 kWh** (±50–100% heuristic uncertainty)
- Total estimated development CO₂eq: **~2.0 kgCO₂eq** (±50–100%)
- Measured current arc (AI-assisted development, 2026-05-22 → 2026-05-23): ~547 Wh,
  ~203 gCO₂eq (±25%, HIGH confidence)

**Your deployment's footprint:**

This depends on your host, region, and workload. Shepard does not phone home
to report runtime energy. If your funding body requires a runtime estimate:

1. Determine your host region and find its carbon intensity from
   [Electricity Maps](https://app.electricitymaps.com/) or your
   national grid operator.
2. Measure or estimate your server's idle + active power draw.
3. Multiply: `kWh × gCO₂eq/kWh = gCO₂eq`.

---

## What to tell your funding body

The following text is pre-cleared for copy-paste into a DMP or sustainability
section. Adapt the deployment details in brackets:

> **DMP sustainability section (suggested language):**
>
> This project uses Shepard, an open-source research data management platform
> developed at DLR with AI-assisted tooling. The development-side carbon footprint
> of the software is tracked per-commit in a public append-only log at
> `aidocs/sustainability/00-energy-estimation-log.md` (repository:
> https://github.com/noheton/shepard). Over the backfilled development history
> (214 commits through 2026-05-26), estimated total inference + build + CI energy
> is approximately 5.4 kWh (±50–100%), corresponding to approximately 2.0 kgCO₂eq
> using per-stream regional carbon intensities per the project's published methodology
> (`aidocs/sustainability/01-methodology.md`).
>
> Production runtime for this project's instance is hosted on
> [your host / region]. Server power draw is approximately [your estimate] W
> under typical load. Carbon intensity for [your region] is approximately
> [your value] gCO₂eq/kWh per [your source, e.g. Electricity Maps 2024].
>
> No net-zero claims are made on behalf of the software or its development
> toolchain. The project tracks energy and CO₂eq to inform honest reporting,
> not to claim offsetting or carbon-free status.

---

## No greenwash

Anthropic (Shepard's AI inference provider) and Hetzner (the reference production
host) both hold renewable energy certificates (RECs) for their infrastructure.
**Shepard does not use these RECs to claim net-zero or carbon-free development.**

Annual matched-renewable certificates reduce the *yearly accounting* total for a
data centre operator, but they do not mean the marginal kilowatt-hour consumed
at the moment of inference was carbon-free. Shepard's energy log applies the
regional grid carbon intensity (DE Strommix, PJM/Virginia) without the REC
discount. This is the honest position recommended by the
[Science Based Targets initiative](https://sciencebasedtargets.org/) and by
[Electricity Maps' methodology](https://www.electricitymaps.com/methodology)
for reporting actual rather than market-based emissions.

If your funding body specifically requires market-based accounting (i.e., counting
RECs), the log methodology can be re-run against the REC-adjusted zero-carbon
baseline — contact the maintainers for the adjusted figures. The default export
from the log is physical/location-based.

---

## Further reading

- [`aidocs/strategy/107-sustainability-policy.md`](https://github.com/noheton/shepard/blob/main/aidocs/strategy/107-sustainability-policy.md) — contributor-facing policy: what we track, what we claim, what we don't
- [`aidocs/sustainability/00-energy-estimation-log.md`](https://github.com/noheton/shepard/blob/main/aidocs/sustainability/00-energy-estimation-log.md) — per-commit energy ledger (SSOT)
- [`aidocs/sustainability/01-methodology.md`](https://github.com/noheton/shepard/blob/main/aidocs/sustainability/01-methodology.md) — per-stream regional methodology + citations
