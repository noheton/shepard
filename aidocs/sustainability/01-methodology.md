---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 01 — Energy / CO₂ estimation methodology

**Status.** **Live, v1.** Companion to
[`00-energy-estimation-log.md`](00-energy-estimation-log.md) (the SSOT
ledger). This doc fixes the citation set, the per-stream heuristics,
and — critically — the **regional carbon-intensity mapping per energy
stream**. Audience: anyone adding a row to the log, anyone
recomputing the backfill, anyone asking "how did you get that number?"

**Companion rules** (from `CLAUDE.md` + memory):
- `feedback_energy_log_per_commit.md` — the per-commit-log rule.
- _Honest companion_ — proprietary-inference numbers carry ±50%
  bands; this doc states the uncertainty up front, not in a
  footnote.

---

## 1. The four energy streams

Every commit accrues energy across (up to) four streams. **Each
stream is grounded in a different physical region, so each gets a
different grid-carbon-intensity factor.** The user flag of
2026-05-23 made this split explicit; v0 of the methodology
collapsed everything onto DE grid and was wrong.

| Stream | What it counts | Physical region | Carbon-intensity source |
|---|---|---|---|
| **LLM inference** | Tokens in + tokens out for AI-assisted work that led to the commit | **Anthropic infra** (primarily AWS US: us-east-1 Virginia + us-west-2 Oregon; minor GCP us-central1 Iowa per Anthropic-Google partnership) | Electricity Maps + AWS Sustainability Dashboard + EPA eGRID 2023; **worst-case us-east-1** when split unknown |
| **Local build / dev-box** | `mvn package`, `npm build`, `docker build`, local test runs implicated by the commit | **DE grid** (dev box in Germany) | Umweltbundesamt Strommix 2024 |
| **CI runtime** | GitHub Actions minutes × runner power draw | **Azure us-east** (GitHub-hosted runners default) | GitHub Sustainability Disclosures + Azure regional carbon (Electricity Maps for the region) |
| **Production runtime** | Continuous backend / DB / frontend cost on prod (apportioned per commit only if the commit changes the steady-state draw) | **Hetzner Falkenstein, Germany (FSK1)** — verified 2026-05-27; Hetzner FSN datacenter, DE grid zone | Electricity Maps zone `DE` (Germany annual average, Umweltbundesamt); Hetzner purchases 100% renewable energy certificates (RECs) for German DCs — we do **not** discount on this basis (same annual-vs-hourly argument as §2.1) |

**Methodology v1 vs v0.** v0 (never shipped to the log; existed
only as the rule in `feedback_energy_log_per_commit.md` for ~half a
day before the user course-corrected) applied DE grid to all four
streams. v1 — this doc — applies the per-stream regional mapping
above. If any backfill rows ever get computed against v0, they get
a `v0-DE-blanket` marker and an `aidocs/16` row to recompute
against v1.

## 2. Regional intensities used (v1)

Numbers below are the values plugged into the log spreadsheet.
Sources are cited inline; all are public.

### 2.1 LLM inference — Anthropic regions

Anthropic does not publish a regional-split-per-request map. Best
available signal:

- **Primary partner**: AWS. Amazon invested $4B in Anthropic in
  2023 and a further $4B in 2024, with Anthropic committing to AWS
  as primary cloud and to using Trainium chips. Bulk of Claude
  inference therefore runs on AWS US infrastructure.
- **Likely region weighting**: heavy on **us-east-1 (Virginia)**
  with **us-west-2 (Oregon)** as secondary; AWS publishes both as
  primary regions for ML workloads.
- **Minor GCP capacity** per the Anthropic-Google partnership
  (2023, expanded 2024). GCP us-central1 (Iowa) is the canonical
  GCP-AI region.

Intensities used in the log:

| Region | gCO₂eq/kWh | Source / vintage |
|---|---:|---|
| AWS us-east-1 (Virginia, PJM grid) | **290** (range 270–310) | Electricity Maps 2024 annual average; EPA eGRID 2023 PJM West subregion |
| AWS us-west-2 (Oregon, PNW hydro) | **85** (range 70–100) | Electricity Maps 2024; EPA eGRID 2023 NWPP |
| GCP us-central1 (Iowa, MRO grid) | **430** (improving as wind comes online) | Electricity Maps 2024; EPA eGRID 2023 MROW |

**Conservative-baseline choice for the log.** Without a credible
split, we use **us-east-1 (290 gCO₂eq/kWh) as the central
estimate** and quote the **us-west-2 lower bound (85)** in the
range column so the reader sees the band. We **do not** apply
Anthropic's or AWS's RE100 / matched-renewable claims — annual
matched renewable is real but ≠ hourly carbon-free, and the honest
companion section of `feedback_energy_log_per_commit.md` says
don't claim "net zero" on that basis.

**Per-token energy.** Anthropic does not publish Wh/token for
Claude. Best public proxies:

- Patterson et al. (Google, 2024) "Empirical analysis of LLM
  inference energy" — ~0.3 Wh/query at small scale; large-context
  windows scale roughly linearly.
- Hugging Face "AI Energy Score" (2025) — order-of-magnitude
  10⁻⁴ to 10⁻³ Wh per token for frontier models.
- Sustainable AI Lab (Cambridge, 2024) — ~1 Wh per 1k output
  tokens as a rough rule-of-thumb for GPT-4-class models.

The log uses **1 Wh per 1k tokens (combined in+out)** as the
central estimate with a **±50% band** marked explicitly per row.
This is the dominant uncertainty in the whole exercise — the
grid-intensity column is the easy part.

### 2.2 Local build / dev-box — DE grid

Single number, well-sourced:

- **DE grid**: **380 gCO₂eq/kWh** (Umweltbundesamt "Strommix
  2024" — annual average emission factor for the German
  electricity mix; published April 2025 for full-year 2024 data).
- For hourly variability (ENTSO-E publishes 15-min resolution):
  the annual average is fine for the log's gradient-tracking
  purpose; hourly precision is a nice-to-have for a later v2.

Dev-box power draw assumption: **~80 W steady state** for the
laptop/workstation during active development, **~15 W idle**.
A typical `mvn package` cycle is ~3 min × 80 W = 4 Wh; a typical
`npm build` is ~1 min × 80 W = ~1.3 Wh. `docker build` for backend
patched image: ~30 s × 80 W = ~0.7 Wh. Test runs: per-suite
heuristic in §3.

### 2.3 CI runtime — GitHub Actions runner region

- GitHub-hosted runners run on **Azure**, default region
  **East US 2 (Virginia)** for standard Linux runners.
- **Azure East US 2**: **~310 gCO₂eq/kWh** (Electricity Maps 2024;
  same PJM grid as AWS us-east-1).
- Runner power draw per minute: published GitHub
  Sustainability Disclosures put a 2-core ubuntu runner at
  **~12 Wh per CPU-hour** (0.2 Wh/min) under load.

### 2.3a CI attribution rule (v1) — which commit owns a PR's CI minutes?

**Rule (v1 — may be refined when actual GHA usage data is analysed,
SUST-CI-ATTRIBUTION).**

- **Merged PR**: the **head commit** of the merged PR owns all CI
  minutes triggered by that PR's CI run. Intermediate commits in the
  PR branch do not receive attribution; attribution sits on the merge
  point.
- **Direct push to `main`**: the pushed commit owns the CI minutes
  for the workflow run it triggered.
- **Squash merge**: the squash commit owns the CI minutes for the
  entire squash. The squashed intermediate commits carry 0 CI
  attribution.

**Rationale.** Most CI minutes for a PR are deterministic at merge
time — the full test suite runs once on the head commit (or the merge
commit). Attributing to the head commit is:

1. *Simpler*: reproducible from `git log` without inspecting the
   workflow run trigger.
2. *Conservative*: slightly over-attributes to the final commit
   rather than distributing minutes across push history where the
   per-commit allocation would be arbitrary.
3. *Accurate in aggregate*: the total CI cost for a PR is fully
   captured; only the per-commit distribution varies.

**Boundary case — force-pushed / amended commits.** A force-push or
amend that triggers a CI re-run attributes the re-run to the amended
commit, not the original. The total CI cost for the logical PR
remains accurate; the per-commit column may show a larger number on
the amended commit than on the pre-amendment one. This is acceptable
for v1 as the aggregate remains correct.

**Boundary case — matrix builds.** When a workflow runs multiple
parallel matrix jobs (e.g., JDK 17 + JDK 21 for the backend test
suite), the CI minutes for all matrix arms are summed and attributed
to the triggering commit as a single total.

### 2.4 Production runtime — nuclide.systems hosting

- **Region**: **Hetzner Falkenstein, Germany — data center FSK1**
  (Falkensteinbergwerk). Verified 2026-05-27 against Hetzner
  product documentation and ops-deploy notes. The "verify per
  provider" open question from the v1 draft is resolved; no
  recompute of prior log rows is needed (§2.4 was already using
  380 gCO₂eq/kWh DE grid in any backfill rows computed after
  2026-05-23).
- **Carbon-intensity source**: Electricity Maps zone `DE`
  (Germany annual average). Umweltbundesamt *Strommix 2024*
  figure: **380 gCO₂eq/kWh**.
- **Hetzner REC posture**: Hetzner publishes a "100% renewable
  energy" pledge for its German data centers, backed by annual
  renewable energy certificates (RECs). Per §7 honest-companion
  policy: annual RECs ≠ hourly carbon-free energy; we do **not**
  discount the DE-grid intensity on that basis. The true
  marginal-hour intensity when this server is running is bounded
  by the DE annual average on the conservative side; the REC
  purchase shifts the yearly accounting but not the marginal
  dispatch. If Electricity Maps real-time data is ever captured
  per-commit, the hourly figure from zone `DE` would replace the
  annual average (v2 methodology trigger — see §6.2).
- Per-commit attribution: only when a commit measurably changes
  steady-state draw (e.g., adds a new background job, removes a
  cache). Most commits are 0-attribution here.

## 3. Per-commit heuristics

The log row is computed as:

```
total_Wh = inference_Wh + local_build_Wh + ci_Wh + prod_delta_Wh
total_gCO2 = sum(stream_Wh * stream_gCO2_per_kWh / 1000)
```

Heuristics for each stream when measurement is unavailable:

| Stream | Heuristic | Confidence |
|---|---|---|
| Inference (this session, known tokens) | `tokens/1000 * 1 Wh ± 50%` | HIGH |
| Inference (older session, unknown) | `commit_size_loc / 100 * 5 Wh ± 100%` | LOW |
| Local build (commit touches backend) | `+5 Wh` (mvn + docker) | MEDIUM |
| Local build (commit touches frontend) | `+2 Wh` (npm + docker) | MEDIUM |
| Local build (doc-only) | `0 Wh` | HIGH |
| CI run (PR commit) | `runner_minutes * 0.2 Wh` | MEDIUM |
| CI run (push to feature branch, no PR) | `0 Wh` (CI doesn't run) | HIGH |
| Prod delta | `0 Wh` unless commit message flags steady-state change | HIGH |

## 4. Confidence tags

Each log row carries one tag end-to-end:

- **HIGH** — commit made in this session; token counts captured
  from the chat transcript; build energy from a current
  measurement.
- **MEDIUM** — commit made with a similar workflow to a recently
  measured one; heuristic-derived but recent.
- **LOW** — older commit, very rough estimate from commit
  metadata alone. Backfill rows before 2026-05-23 are all LOW.

## 5. Reporting bands

Every per-stream cell in the log carries either an explicit ± band
or inherits the default band for its confidence tag:

- HIGH → ±25%
- MEDIUM → ±50%
- LOW → ±100% (i.e. could be 2× off in either direction)

When summing across commits for a session-or-week total, the
bands compound; the log's footer reports `central_estimate
[low_bound, high_bound]` not a single number.

## 6. What changes (v2 and beyond)

When any of these arrive, the methodology bumps to v2 and the log
recomputes the affected rows:

1. **Anthropic publishes regional-split data or per-token energy**
   — the inference column gets a precise number, the ±50% band
   shrinks to ±10–20%.
2. **Hourly grid-intensity ingestion** — switch from annual
   averages to ENTSO-E / Electricity Maps hourly for commits with
   a captured timestamp.
3. **GitHub Actions publishes per-runner gCO₂** — replace the
   `0.2 Wh/min` heuristic with the published number.
4. **Substrate captures `shepard:energyWh` natively** — the log
   stops being a separate doc and becomes a query over the
   provenance graph; this doc becomes the human-readable
   companion.

## 7. Honest companion (carried from feedback memory)

> The point is to **track the gradient**, not to claim a precise
> number. A session that produces 30 commits has X× the inference
> energy of a session that produces 3 commits. That ratio survives
> the uncertainty.

The ±50% band on the inference column is real and will not shrink
without primary-source disclosure from Anthropic. The DE-grid and
PJM-grid columns are well-sourced and tight. Reporting a single
"this commit cost N grams CO₂" without the band is the bug; every
row in the log carries the band.

## 8. Citations

- Umweltbundesamt (2025). *Strommix Deutschland 2024 — annual
  emission factor.* https://www.umweltbundesamt.de/
- Electricity Maps (2024). *Annual carbon-intensity datasets per
  region.* https://app.electricitymaps.com/
- EPA (2023). *eGRID2023 — Emissions & Generation Resource
  Integrated Database.* PJM West, NWPP, MROW subregions.
- Patterson, D. et al. (2024). *Empirical analysis of LLM
  inference energy.* Google Research. (Verify exact citation
  before linking from a public artefact.)
- Hugging Face (2025). *AI Energy Score Leaderboard.*
  https://huggingface.co/spaces/AIEnergyScore/Leaderboard
- Sustainable AI Lab Cambridge (2024). *Per-token energy
  estimates for frontier LLMs.* Working paper.
- GitHub (2024). *Sustainability disclosures — Actions runner
  energy.* https://github.com/sustainability
- AWS (2024). *Sustainability Dashboard — regional carbon
  intensity.* https://sustainability.aboutamazon.com/
- Anthropic + AWS partnership announcements (2023, 2024) —
  $4B + $4B investment, AWS as primary cloud, Trainium.
- Anthropic + Google partnership (2023, expanded 2024) — GCP
  TPU capacity.

(Entries to add to `docs/_data/references.bib` in the same PR
that ships the first log rows, per
`feedback_bibliography_maintenance.md`.)
