---
stage: concept
last-stage-change: 2026-05-26
---

# MFFD CRW Welding Time-Series — Analysis Case Definitions

**Role:** Applied ML Engineer and Industrial Process Data Scientist  
**Date:** 2026-05-26  
**Scope:** Three concrete analysis cases for the MFFD stringer-welding dataset
(137 `.svdx` CRW passes + 6 000–6 500 TIFF thermal images per pass + 21 spot-weld
`.svdx` files). Ground truth from literature; Shepard capability audit grounded
in actual source files.

---

## 1. What the Data Contains

### Dataset inventory (deduced from directory names + confirmed by literature)

The DLR elib 200851 paper ("Production of the Thermoplastic Composite Upper Shell
for the MFFD") and the SAMPE/scitepress 2023 conference paper (Fischer/Endrass)
confirm the exact welding technologies and instrumentation used.

**Stringer welding (`Stringer_schweissungen/`):** 137 `.svdx` files, ~288 GB.
Nine identifiable stringer passes:

| Pass ID | Probable stringer |
|---------|-----------------|
| P02_1teBahn | Stringer P02, first run |
| P04Strich_S_1teBahn | Stringer P04', skin side, first run |
| P07Strich_S_2teBahn | Stringer P07', skin side, second run |
| P08Strich_2teBahn | Stringer P08, second run |
| P14_S_2teBahn | Stringer P14, skin side, second run |
| P14Strich_S_1teBahn | Stringer P14', skin side, first run |
| P15_S_2teBahn | Stringer P15, skin side, second run |
| P16_S_2teBahn | Stringer P16, skin side, second run |
| P18_S_1teBahn | Stringer P18, skin side, first run |

Each pass directory contains: `.tiff` thermal images (6 000–6 500 per pass at the
FLIR-class IR camera rate of ~50 Hz → ~2 min weld duration per pass), `.mp4` video,
one `.svdx` scope file. 2023-03-22 to 2023-04-04 campaign dates span 14 calendar
days, consistent with a 44-stringer fuselage programme.

**Spot welding (`Punktschweißungen/`):** 21 `.svdx` files, ~6 GB.
Two trigger groups: TriggerGroup_258 and TriggerGroup_260. These correspond to the
resistance welding of C-frames / frame-couplings — a discontinuous (step-and-weld)
process with defined consolidation hold time per spot.

**Collins commissioning data (`Scope_Sicherung/Collins/`):** 7 `.svdx` + 8 `.csv`,
from 2022. Pre-production equipment validation at Collins Aerospace. Baseline
parameters established before the 2023 production campaign.

### Channel set — what a CRW SVDX file most likely contains

Based on the DLR/WELDER MDPI 2025 paper ("Advanced Real-Time Monitoring of New
Welding Processes in the Aircraft Industry") and the scitepress 2023 paper
(Fischer/Endrass), the resistance welding TwinCAT Scope measures via
Beckhoff ELM3704-0000 high-precision analog input terminals at 1 ms EtherCAT
cadence. Confirmed channels in MFFD resistance welding:

| Channel name (likely) | Physical quantity | Unit | Role |
|-----------------------|-------------------|------|------|
| `Voltage_U` | Applied voltage across heating element | V | Control setpoint/actual |
| `Current_I` | Welding current | A | Primary heat source (P = I²R) |
| `Load_F` (or `Pressure_bar`) | Consolidation force or pneumatic pressure | N or bar | Consolidation quality |
| `Temperature_TC_01..20` | 20 thermocouple channels in the welding head | °C | Melt-front localisation |
| `Position_X` (CRW only) | Travel position along the weld seam | mm | Spatial registration |
| `Speed_v` (CRW only) | Travel speed | mm/s | Energy input density |

For **continuous resistance welding** (CRW, stringer passes), the SAMPE 2024
"Characterization of a Test Bench System for CRW" paper (Palardy-Sim et al.,
UBC/NRC/DLR collaboration) and the DLR elib 209463 "Model-Based Control of
CRW" paper identify the three control inputs as **power, pressure, and speed**,
with **weld interface temperature** as the critical output (indirectly estimated
since direct measurement is invasive). The Beckhoff scope logs all three control
inputs plus the 20 head thermocouples at 1 ms cadence.

For **spot welding** (TriggerGroup_258/260), the WELDER monitoring paper confirms
only three channels are tracked: **voltage, load, temperature**. Spot welds have a
defined trigger-to-trigger sequence (clamp → heat → cool → release); TriggerGroup ID
is the grouping key.

The `.svdx` file is a non-disclosed proprietary Beckhoff format. Extraction pipeline
is described in §5.

### Inferred data volume

Assumptions from literature:
- 1 ms EtherCAT cadence → 1 000 samples/s per channel
- CRW stringer pass duration: ~2 min (6 000 TIFFs at ~50 Hz = 120 s)
- Channels per pass: ~27 (20 thermocouples + voltage, current, load/pressure, position, speed, 1 status flag)
- 137 stringer passes: 137 × 120 s × 1 000 sps × 27 channels = **~444 M data points**

Spot welding adds ~6 GB / 21 files. If spot weld durations are ~30 s each:
- 21 files × 30 s × 1 000 sps × 3 channels ≈ **1.9 M data points**

Collins commissioning (2022): 7 svdx × smaller scope, estimated 5–10 M points.

**Working estimate: ~450 M data points total** (dominated by stringer passes).
This is a large but tractable TimescaleDB load; see §8 for infrastructure analysis.

---

## 2. Analysis Case A — Weld Seam Energy Budget

### What it answers

> "Did pass P08_2teBahn deliver enough Joule energy to achieve full consolidation,
> and how does it compare to the 44 other stringer passes from the same campaign?"

### Physical basis

Joule's law: Q = I²Rt, where Q (J/m) is the specific energy density at the weld
interface, I is current (A), R is the heating element resistance (Ω, near-constant
for CF fabric), t is time. For CRW, Q_linear = (I² × R × dt / dx) where dx is the
incremental travel distance. The quality ground truth is SLSS (Short Lap Shear
Strength); Pintos et al. (2023, SAMPE/Scipedia, WELDER project) found optimal
parameters at 85–130 kW/m² specific power over 30–50 s weld time, achieving
62.8–68.6% of base material shear strength.

### Algorithm recommendation

**Step 1: Numerical integration of energy per seam.** Compute:
```
Q_seam = ∫ I(t)·V(t) dt   [Wh]
```
or equivalently per-unit-length:
```
Q_linear(x) = I(x)·V(x) / v(x)   [J/mm]
```
where v(x) is travel speed at position x. This produces one scalar Q_seam per
pass and one profile Q_linear(x) per pass.

**Step 2: Outlier detection with Isolation Forest** trained on {Q_seam, ΔQ_linear_max,
T_peak_mean, F_consolidation_mean} per pass. Isolation Forest (scikit-learn) handles
the small dataset (137 passes) correctly, requires no labelled anomalies, and
outperformed OCSVM and LOF in the MDPI 2020 micro-RSW quality study (AUC 0.9525
at 1.79 s training time on 60 000 welds). At 137 passes, training time is
negligible.

**Step 3: Process window compliance check.** Flag passes where Q_seam falls
outside ±2σ of the campaign mean, or where any 50 mm window in Q_linear(x) drops
below 80% of the target specific power (tunable threshold). This is a deterministic
rule, not ML — matches the Design of Experiments approach used in the MFFD
commissioning (Fischer/Endrass 2023).

**Why not CUSUM?** CUSUM is ideal for detecting a sustained mean-shift in a
single ongoing process (drift over time in a production line). For the MFFD dataset,
the primary comparison axis is across 137 discrete seams, not within one seam —
Isolation Forest on per-seam summary statistics is the correct framing.
CUSUM remains useful within Case B (inter-seam drift detection).

### Output

Per-pass quality table: {pass_id, Q_seam_Wh, Q_linear_min_Jmm, T_peak_mean_C,
anomaly_score, flag: PASS/WARN/FAIL}. Storable as a `StructuredDataReference`
on the DataObject representing each stringer weld.

---

## 3. Analysis Case B — Inter-Seam Drift Detection

### What it answers

> "Did the welding system drift between the 2022 Collins commissioning and the
> 2023 production campaign? Did parameter drift occur across the 14-day 2023 campaign?"

### Physical basis

Continuous resistance welding equipment experiences electrode contact resistance
drift, heating element resistance ageing, and pneumatic pressure stability changes.
The De Paepe et al. 2022 paper ("An Incremental Grey-Box Current Regression Model
for Anomaly Detection of Resistance Mash Seam Welding") addressed exactly this
class of problem — process drift over a production campaign — and proposed an
incrementally-updated grey-box model to separate controlled variance from equipment
drift.

### Algorithm recommendation

**Phase B1: 2022 vs 2023 baseline shift.** The Collins 2022 data represents
commissioning under controlled laboratory conditions. The 2023 production data
covers 14 days under industrial conditions. Compute the channel-wise summary
statistics (mean, σ, P10, P90) for {U, I, F, T_TC_mean} for each campaign.
Test for distributional shift using the **Kolmogorov–Smirnov two-sample test**
(scipy.stats.ks_2samp) or **Maximum Mean Discrepancy** (sklearn or FMMD).
Expect rightward shift in current/voltage if the heating element resistance
aged (Q = I²R → operator increases I or V to maintain Q); expect spread
increase in T_TC if thermocouple contact degraded.

**Phase B2: Within-campaign drift across 14 days.** Apply
**ADWIN (Adaptive Windowing)** algorithm (river-ml Python library) on the
per-pass summary statistics as a stream ordered by pass start-time.
ADWIN detects the statistical change point at minimal memory cost (adaptive
window, no fixed window size required) and is the 2025 state-of-the-art for
online change detection in manufacturing with small labeled datasets. Cite: Bifet
and Gavalda 2007 "Learning from Time-Changing Data with Adaptive Windowing" (SIAM).

**Phase B3: Ordinal regression on drift signal.** If the ADWIN output identifies
a change point date, tag all passes before and after with the change-point label
and fit a simple linear trend (scipy.stats.linregress) to the energy budget
Q_seam over the ordered pass index. A slope significantly different from zero
(p < 0.05) indicates systematic drift, not random noise.

**Why ADWIN over CUSUM here?** CUSUM requires a pre-specified magnitude of shift
to optimise the reference parameter h. For inter-seam drift over 14 days, the
shift magnitude is unknown a priori. ADWIN is parameter-free for the drift
magnitude and has strong theoretical guarantees on false detection rate.

### Output

Drift report: change point(s) with date and affected channel(s); before/after
distribution comparison table; ADWIN alarm timestamps. Storable as a
`LabJournalEntry` or `StructuredDataReference` on the collection-level DataObject
representing the campaign.

---

## 4. Analysis Case C — TIFF Thermal Trail Quality Map

### What it answers

> "For a given stringer pass, where along the 8-metre seam did the weld temperature
> drop below the LM-PAEK processing window? What is the spatial QA map?"

### Physical basis

LM-PAEK (Low-Melt Polyaryletherketone) melts at ~300 °C and degrades above ~380 °C.
The IR camera captures the surface temperature field at ~50 Hz. Each TIFF is one
frame; the sequence of 6 000–6 500 frames × 50 Hz = 120 s is the full pass.
The seam location moves through the camera field of view as the welding head travels.

The HELTHY technique (Shi et al. 2008/2009, TU Delft) exploits the same embedded
heating element for NDI: after welding, the element is re-energised at low power
and the surface temperature field is imaged. Voids and delaminations appear as
hot or cold spots depending on insulation geometry. This is the closest published
analog for TIFF-based resistance weld quality mapping.

The OSTI/ORNL work (Guo et al. 2021, "Predicting Nugget Size Using Infrared Thermal
Videos") demonstrated CNN-based prediction of weld nugget dimensions from spatial-
temporal IR sequences at 100 Hz for resistance spot welding.

### Spatial features to extract from each TIFF frame

| Feature | Physical meaning | Extraction method |
|---------|-----------------|-------------------|
| T_peak | Maximum temperature in ROI | np.max(frame[roi]) |
| T_mean_seam | Mean temperature in ±5 mm band around seam | np.mean(frame[seam_band]) |
| HAZ_width | Width of heat-affected zone (FWHM of cross-track T profile) | scipy.signal.peak_widths |
| ΔT_left_right | Asymmetry of T across seam centreline | np.mean(L) − np.mean(R) in seam band |
| T_gradient_x | Along-seam temperature gradient | np.gradient(frame, axis=1)[seam_row] |

### Algorithm recommendation

**Step 1: Seam localisation** in each TIFF frame using the maximum-temperature
row (robust for CRW where the seam is the hottest zone). Apply a 3-frame temporal
median filter to suppress IR camera noise before localisation.

**Step 2: Build the spatial quality profile.** Map each frame to its X-position
along the seam using the `Position_X` channel from the corresponding SVDX file
(temporal registration: TIFF frame timestamp ↔ SVDX sample timestamp).
Result: T_peak(x), HAZ_width(x), ΔT_asymmetry(x) as functions of seam position x.

**Step 3: Anomaly detection on the spatial profile.** For T_peak(x): flag any
100 mm window where T_peak falls below the LM-PAEK processing window lower bound
(300 °C nominal; exact threshold from the MFFD parameter set as established in
Fischer/Endrass 2023). For HAZ_width(x): flag windows where width narrows to
<50% of nominal (potential cold weld) or widens to >200% (overheating with matrix
squeeze-out). Algorithm: **1D Isolation Forest** on the spatial feature vector
window (rolling window size 50 frames), or equivalently **CUSUM** on T_peak(x)
since the spatial profile IS an ordered sequence with a known target value.

**Step 4: Produce the quality heatmap.** Render as a 2D raster:
Y = cross-track position (in pixels), X = along-seam position (in mm). Each cell
coloured by min(T_peak) within a 10 mm bin. Store as PNG in a FileReference on
the DataObject.

**ML complexity note:** A full CNN (Guo/ORNL approach) requires hundreds of
labelled sequences. At 137 passes with no ground-truth NDT labels for each
frame, CNN training is infeasible. The algorithm chain above requires no
training labels — it is fully unsupervised with domain-derived thresholds.
A CNN approach becomes viable once NDT results (ultrasonic C-scan per seam)
are linked to the TIFF sequences (currently absent from the dataset).

### Output

Per-seam quality raster PNG + CSV spatial quality profile. The raster is the
"Trace3D analogue" for thermal data — matches the `aidocs/agent-findings/trace3d-spike.md`
design for 2D spatial payloads over a 1D spatial axis.

---

## 5. Required Preprocessing Pipeline

### Stage 1: SVDX → TDMS (Windows tool required)

`.svdx` is a non-disclosed Beckhoff proprietary format. The Beckhoff information
system (infosys.beckhoff.com) states explicitly: "A .svdx file is a specific,
non-disclosed data format." Extraction requires the **Beckhoff TC3 Scope ExportTool**
(`TC3ScopeExportTool.exe`), which ships with TF3300 Scope Server and TE1300 Scope
View. It exports to tdms, csv, svb, or dat.

**Known tool location:** `tool_sources/` in the MFFD dump (aidocs/108) contains
47 444 prototype tools; the ExportTool is likely among them.

Command-line invocation (from Beckhoff docs):
```
TC3ScopeExportTool.exe "svd=<path.svdx>" "target=<out.tdms>" silent
```

**Platform constraint:** This is a Windows executable. On Linux (the nuclide
dev box), run under Wine 8.x or in a Windows Docker container:
```bash
wine TC3ScopeExportTool.exe "svd=/mnt/data/P08_2teBahn.svdx" "target=/tmp/P08.tdms" silent
```
Wine handles simple Win32 CLI tools acceptably. If Wine fails (common with
TwinCAT system services), spin up a Windows 10 Docker container via
`ghcr.io/runhyve/windows:10` or run the export on the DLR cube (Windows).

### Stage 2: TDMS → CSV / DataFrame (Python, cross-platform)

```python
import nptdms  # pip install npTDMS[pandas]
import pandas as pd

with nptdms.TdmsFile.open("P08_2teBahn.tdms") as f:
    # Inspect channel structure
    for group in f.groups():
        for channel in group.channels():
            print(group.name, channel.name, len(channel))
    
    # Load all channels as a DataFrame
    df = f.as_dataframe(time_index=True, absolute_time=True)
```

npTDMS 1.10.0 (latest, 2025-04-22) supports `.tdms` from TwinCAT Scope via
standard NI-TDMS parsing. The `time_index=True, absolute_time=True` options
materialise the Beckhoff file-time timestamps (1 tick = 100 ns, origin = 1601-01-01)
as UTC datetime64. Apply `pd.to_datetime(origin='2000-01-01')` if the epoch
differs between export modes.

### Stage 3: Temporal alignment with TIFF sequence

Each stringer pass has `.tiff` files named with a frame counter and/or timestamp.
The SVDX `Position_X` channel provides the spatial position at each millisecond.
Align TIFF frames to SVDX time using:

```python
# Assume TIFFs named P08_00000.tiff, P08_00001.tiff, ... at 50 Hz
tiff_times_ns = np.arange(len(tiff_files)) * (1e9 / 50)  # 20 ms spacing
tiff_times_ns += svdx_start_ns  # offset to SVDX epoch

# Match each TIFF to nearest SVDX sample
tiff_positions_mm = np.interp(tiff_times_ns, df.index.astype(np.int64), df['Position_X'])
```

### Stage 4: Ingestion into Shepard TimescaleDB

Once per-channel CSV is available, use the existing import manifest pipeline
(`POST /v2/import/manifests`) with `TimeseriesReferenceEntry` per channel.
Channel naming convention for 5-tuple:

| 5-tuple field | Value |
|--------------|-------|
| `measurement` | `stringer_welding` (or `spot_welding`) |
| `field` | channel name, e.g., `Voltage_U`, `Current_I`, `Temperature_TC_01` |
| `symbolicName` | pass ID, e.g., `P08_2teBahn` |
| `device` | `beckhoff_crw_head` |
| `location` | stringer ID, e.g., `P08` |

**Until TS-IDa/IDb ship** (single `appId` for each channel), the 5-tuple
is required. Every downstream analysis script must embed all 5 fields.
See `aidocs/agent-findings/analytics-ai.md` §"The 5-tuple ML pipeline tax" for
the structural fix.

---

## 6. Best ML Approach per Case

| Case | Primary algorithm | Justification | Training labels needed |
|------|-----------------|---------------|----------------------|
| **A: Energy budget** | Isolation Forest on per-seam summary vector | 137 passes, no labels, handles multivariate outliers, AUC 0.95 in analogous RSW study (MDPI 2020) | None |
| **A: Process window** | Deterministic threshold rules on ∫I·V dt | Physical model well-characterised (Joule's law + MFFD process window from Endrass 2022b DoE) | None |
| **B: 2022 vs 2023 shift** | KS two-sample test per channel | Non-parametric, exact at small N, interpretable p-value | None |
| **B: Within-campaign drift** | ADWIN streaming change detector | Parameter-free for drift magnitude, theoretically grounded (Bifet & Gavalda 2007), available in `river-ml` | None |
| **C: TIFF spatial QA** | Rolling 1D Isolation Forest on spatial feature vector | Unsupervised at 137 seams; CNN infeasible without NDT ground truth per frame | None |
| **C: HAZ thresholding** | CUSUM on T_peak(x) with LM-PAEK process window lower bound | Known target temperature, ordered sequence → CUSUM is optimal for one-sided sustained shift | None |
| **Future: defect localisation** | Contrastive learning (SimCLR on TIFF crops) | Once NDT C-scan results available; requires ≥50 labelled seams | Yes (NDT results needed) |

**Why not LSTM autoencoder for Cases A/B?**
LSTM-AE (MDPI 2023, laser welder PMM) requires ~200 training sequences and
~1 500 total samples. At 137 seam passes the dataset is marginal. The 2023
paper achieved 97.3% accuracy on a 1 500-sample industrial dataset (200 selected
from preprocessing). At 137 passes with unknown label distribution, the
unsupervised Isolation Forest is a stronger baseline. LSTM-AE becomes appropriate
once MFFD accrues 3–5× more campaigns or synthetic augmentation is applied.

**Why not foundation model zero-shot (Chronos/MOMENT)?**
MOMENT and Moirai were evaluated on public benchmarks (ETTh, Traffic, Weather,
ETTm). CRW welding current traces, thermocouple ramp profiles, and consolidation
force signatures are not in any public TS pretraining corpus. Distribution shift
from the pretraining domain is large and unquantified. The MAD detector already
shipped as AI1b is a stronger anomaly baseline for this domain. Foundation models
become relevant when the corpus exceeds ~1 000 runs and domain-specific fine-tuning
is feasible.

---

## 7. What Shepard Needs to Add

### 7.1 INTEGRAL aggregation function (CRITICAL for Case A)

`TimeseriesDataPointRepository.java` line ~463:
```java
private void assertNotIntegral(Optional<AggregateFunction> function) {
  if (function.isPresent() && function.get() == AggregateFunction.INTEGRAL) {
    throw new InvalidRequestException(
      "Aggregation function 'integral' is currently not implemented.");
  }
}
```

Case A's weld energy budget is fundamentally `∫ I(t)·V(t) dt`. This requires
either:
- **Option A (preferred):** Implement `INTEGRAL` in `buildSelectQueryObject` as a
  trapezoidal rule over the time window: `SUM(value * (time - LAG(time) OVER ...))`.
  TimescaleDB's `time_weight()` hyperfunction computes a time-weighted integral
  natively.
- **Option B:** Add a dedicated `POST /v2/timeseries-references/{appId}/compute-integral`
  endpoint (returns a scalar over a time window) as an AI-series operation alongside
  the existing `detect-anomalies`.

**Priority: HIGH.** Without this, the energy budget must be computed client-side
(Python script reading the full CSV), which negates Shepard's value as an
analysis substrate.

### 7.2 Cross-channel arithmetic endpoint (MAJOR for Case A)

The weld power `P(t) = I(t) · V(t)` requires multiplying two distinct timeseries
channels at each timestamp. Shepard has no cross-channel arithmetic endpoint.
Options:
- **New endpoint:** `POST /v2/timeseries-containers/{cid}/compute`
  accepting a simple expression `{"expression": "I * V", "channels": {"I": appId1, "V": appId2}}`
  with a time window. Returns a synthetic timeseries of P(t) (not persisted).
- **Client-side workaround (acceptable short-term):** Export both channels as CSV,
  compute in Python, persist the result back as a new timeseries channel
  (`P_instantaneous_W`).

**Priority: MEDIUM.** Client-side is workable for 137 passes; native expression
evaluation becomes important at scale.

### 7.3 Image bundle / TIFF sequence plugin (CRITICAL for Case C)

Case C requires ingesting 6 000–6 500 TIFF frames per pass (~137 × 6 250 = 856 000
TIFFs total). The current FileReference / FileContainer model handles individual
files. There is no "TIFF sequence as a timeseries" primitive.

Required additions:
1. `shepard-plugin-image-bundle` (see `aidocs/project_imagebundle_design.md`):
   ingests an ordered sequence of TIFF/PNG files keyed by frame index or timestamp.
   The plugin provides `GET /v2/image-bundles/{appId}/frames/{n}` and a spatial
   quality overlay renderer.
2. **Temporal registration metadata** linking each frame's timestamp to the
   SVDX `Position_X` sample at that time — stored as an attribute on the
   ImageBundle DataObject.

**Priority: HIGH.** Without this, TIFF sequences must live as loose files in
MinIO/Garage with no Shepard-side query capability.

### 7.4 TS-IDa/IDb migration (MAJOR for all cases)

All analysis scripts must embed the 5-tuple `{measurement, device, location, symbolicName, field}`
on every API call. With 27 channels × 137 passes = 3 699 timeseries identities,
the risk of mismatched 5-tuple lookups in batch scripts is significant. TS-IDa
(expose `timeseriesId` on API responses) and TS-IDb (accept `timeseriesId` on
read endpoints) should ship before any analysis pipeline is built on this dataset.
See `aidocs/platform/87-timeseries-appid-migration.md` and
`aidocs/agent-findings/analytics-ai.md §3`.

**Priority: HIGH.** Zero-risk additive change; unblocks all downstream analysis tooling.

### 7.5 Continuous aggregates for 1 ms data (MAJOR for all cases)

At 1 ms cadence and 137 passes, the `timeseries_data_points` hypertable will hold
~450 M rows. Dashboard queries aggregating by 100 ms buckets over a 120 s window
will scan ~120 000 rows per channel per request. At 27 channels per pass, one
dashboard load = 3.2 M row scans.

`aidocs/data/12-timescaledb-performance-analysis.md §5.2` already recommends
continuous aggregates for 1 min and 1 hour buckets. For CRW data the relevant
buckets are:
- **10 ms** (1/100 of a second — preserves 10 Hz welding dynamics for quality review)
- **100 ms** (quality dashboard: per-welding-head pass summary)
- **1 s** (campaign comparison: per-pass overview)

```sql
CREATE MATERIALIZED VIEW ts_crw_100ms
WITH (timescaledb.continuous) AS
SELECT timeseries_id,
       time_bucket(100_000_000::bigint, time) AS bucket,   -- 100 ms in ns
       AVG(double_value) AS avg_d,
       MIN(double_value) AS min_d,
       MAX(double_value) AS max_d
FROM   timeseries_data_points
WHERE  double_value IS NOT NULL
GROUP  BY timeseries_id, bucket;
```

**Priority: HIGH.** Without CAGGs, every chart load on welding data is a full
scan of 120 000 rows per channel.

### 7.6 shepard-plugin-ai EMBEDDING capability (FUTURE, Case C)

Once NDT ultrasonic C-scan results are available for each seam, the TIFF quality
rasters (Case C output) become training data for a contrastive representation
learner. The `shepard-plugin-ai` EMBEDDING capability (designed in `aidocs/platform/86`)
is the injection point: store the 128-dim embedding of each seam's quality raster
in pgvector, enabling "find seams similar to this defective seam" search.

**Priority: LOW (deferred).** Requires NDT labels + plugin-ai foundation shipping first.

### 7.7 Spatial timeseries JOIN endpoint (FUTURE, Cases A+C)

Case C requires joining TIFF frame timestamps with SVDX Position_X samples. A
`GET /v2/timeseries-containers/{cid}/spatial-join?positionChannel=appId&frameCount=6500`
endpoint that returns `{frame_index, position_mm}` pairs would expose this
registration natively. Today, both exports must be done client-side.

---

## 8. Data Volume and Infrastructure

### Will TimescaleDB handle ~450 M data points?

From `aidocs/data/12-timescaledb-performance-analysis.md` and `aidocs/data/68`:

| Metric | Value | Assessment |
|--------|-------|------------|
| Estimated points | ~450 M | Manageable with post-V1.8.0 schema |
| Storage (uncompressed, double) | ~450 M × 24 bytes = ~10.8 GB | Below nuclide capacity |
| Storage (compressed, ≥10× ratio) | ~1.1 GB | Negligible |
| Ingest rate at 20 k rows/batch | ~137 files × 120 s × 27 ch × 1 000 sps = ~1 000 batches/file | Batched COPY path (`insertManyDataPointsWithCopyCommandBatched`) recommended |
| Query: 120 s window at 1 ms = 120 000 rows/channel | With CAGG at 100 ms: 1 200 rows/channel | **CAGG is mandatory for interactive use** |
| 5-year retention policy | ~2.25 B rows at full run rate | Add `add_retention_policy` before ingest starts |

**Ingest performance caveat:** The `ON CONFLICT DO UPDATE` path
(`insertManyDataPoints`) decompresses chunks for re-uploads. For the SVDX batch
import, use the `insertManyDataPointsWithCopyCommandBatched` path directly — this
bypasses the ORM upsert and writes via PostgreSQL `COPY FROM STDIN`. Each file
should be a single transaction to allow progress reporting and retry from last
committed batch.

**Chunk interval recommendation:** The 1-day default chunk interval is appropriate.
At 1 ms cadence, each day holds ~86.4 M rows/channel; compressed to ~8.6 M rows
per chunk per channel → chunks are comfortably in the compression sweet spot.

### Memory note on TIFF ingest

856 000 TIFFs (6 250 per pass × 137 passes) at ~512 KB each = ~440 GB storage
in Garage. This exceeds the current Garage capacity. Pre-screen TIFFs for:
1. Dark frames (no weld activity → low peak temperature → skip)
2. Downsample to 1 Hz (one TIFF per second) for long-term storage; keep 50 Hz
   only for 30-second windows around detected anomalies.

This requires the ImageBundle plugin's `async 202+poll` import pattern and
a quality-based decimation step before ingest — consistent with the
`aidocs/project_imagebundle_design.md` design.

---

## 9. Gaps and Blockers (Summary)

| Gap | Blocks | Effort | Priority |
|-----|--------|--------|----------|
| INTEGRAL aggregation not implemented | Case A per-seam energy budget as first-class Shepard workflow | 1 day | CRITICAL |
| SVDX → TDMS requires Windows ExportTool | All cases — no SVDX data in TimescaleDB yet | 0.5 day (Wine/Docker setup) + bulk export time | CRITICAL |
| TS-IDa/IDb migration not shipped | All analysis scripts stable addressing | 1 day | HIGH |
| Continuous aggregates not defined | Interactive dashboard on 1 ms data | 1 day | HIGH |
| ImageBundle plugin not shipped | TIFF sequence as Shepard-native payload (Case C) | Sprint-scale | HIGH |
| Cross-channel arithmetic endpoint | Case A P(t) = I(t)·V(t) native; workaround: client-side | 2 days | MEDIUM |
| NDT C-scan labels absent | Supervised TIFF defect classifier (contrastive) | External data acquisition | LOW |

---

## 10. What Surprised Me

**The MFFD welding system is already Shepard-native.** Fischer/Endrass scitepress 2023 confirms
that ProcessMonitoring software "stores [measurements] in the central shepard database" and
ProcessControl "uses the shepard database as central data store." This is not a data migration
task — the MFFD system was *designed* around Shepard as its process data substrate.
The `.svdx` files on the N: drive are raw scope backups of what already flows into Shepard
on the DLR cube. The analysis cases here are therefore also Shepard-native from day one,
not bolt-on analytics.

**The INTEGRAL gap is a load-bearing blocker, not a minor feature.** Weld energy budget
is the most direct quality proxy for resistance welding (Joule's law), and it is the
quantity used in every DoE study cited here (specific power in kW/m², Wh per seam).
The existing anomaly detection endpoint (AI1b, rolling MAD) operates on individual
channel samples. A weld quality assessment requires a time-window integral. This is
the single highest-impact missing primitive for welding analytics in Shepard.

**6 000 TIFFs per pass at 50 Hz is not "some images" — it is a 2-minute dense
temporal stack.** The thermal image sequences ARE a timeseries, just spatially
structured. The Trace3D view design (`aidocs/agent-findings/trace3d-spike.md`) is
the right shape for the quality raster output. The ImageBundle plugin and the
TimescaleDB pipeline are two sides of the same media-analysis substrate.

**Collins 2022 commissioning data is a baseline asset.** The 14 months separating
the 2022 commissioning files from the 2023 production campaign is sufficient time
for measurable equipment drift. No published MFFD paper has done this inter-campaign
comparison. Case B is potentially novel research.

---

## References

- Fischer, C., Endrass, M. et al. (2023). "Data-driven Process Monitoring in the MFFD
  Frame Welding Process." SCITEPRESS Digital Library. (Confirms Beckhoff ELM3704 channels,
  OPC/UA, EtherCAT 1 ms cadence, ProcessMonitoring → Shepard integration.)
- DLR elib 200851. "Production of the Thermoplastic Composite Upper Shell for the MFFD."
  (Confirms three technologies: AFP in-situ, CUW, resistance welding; 44 stringers.)
- DLR elib 209463. Endrass, M. "Demonstration of Model-Based Control of Thermoplastic
  Continuous Resistance Welding." (Power + pressure + speed as CRW control triplet.)
- MDPI Materials 2025 (WELDER). "Advanced Real-Time Monitoring of New Welding Processes
  in the Aircraft Industry." (Confirms MFFD resistance-weld channels: voltage, load,
  temperature; OPC UA data transmission to Shepard.)
- Pintos, B. et al. (2023). "Resistance welding of thermoplastic composite: from laboratory
  to scale-up on a fuselage demonstrator." Scipedia / WELDER project.
  (SLSS as quality ground truth; 130 kW/m² × 30 s = 62.8% base material strength.)
- Shi, H. et al. (2015). "Continuous resistance welding of thermoplastic composites:
  Modelling of heat generation and heat transfer." Composites Part A.
  (Power and speed as dominant CRW parameters; non-uniform T profile along seam.)
- Palardy-Sim, M. et al. (2024). "Characterization of a Test Bench System for CRW."
  SAMPE 2024. (UBC/NRC/DLR collaboration; physics-based control model with power,
  pressure, speed as control inputs.)
- De Paepe, D. et al. (2022). "An Incremental Grey-Box Current Regression Model for
  Anomaly Detection of Resistance Mash Seam Welding." Applied Sciences 12(2), 913.
  (Directly applicable grey-box approach for Case B inter-seam drift.)
- Guo, S. et al. (2021). "Predicting Nugget Size Using Infrared Thermal Videos."
  OSTI/ORNL / AWS journal. (IR thermal sequence → CNN for RSW NDE; architecture
  motivation for Case C supervised extension.)
- Bifet, A. and Gavalda, R. (2007). "Learning from Time-Changing Data with Adaptive
  Windowing." SIAM International Conference on Data Mining. (ADWIN foundation for Case B.)
- MDPI Applied Sciences 2020. "Quality Monitoring for Micro Resistance Spot Welding
  with Isolation Forest." (AUC 0.9525 for Isolation Forest on dynamic resistance features
  — foundation for Case A algorithm choice.)
- Beckhoff Information System. "Formats" and "Automated Export." infosys.beckhoff.com.
  (SVDX as non-disclosed proprietary format; TC3ScopeExportTool.exe for conversion.)
- npTDMS v1.10.0. https://github.com/adamreeve/npTDMS. (Python TDMS reader for Stage 2.)
