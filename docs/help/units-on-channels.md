---
title: Units on Timeseries channels
description: How Shepard infers the physical unit of a channel from its field name, and what to do when the inference is wrong or missing
permalink: /help/units-on-channels/
layout: default
audience: user
---
# Units on Timeseries channels

Every time you (or an importer) creates a Timeseries channel, Shepard
looks at the channel's `field` name and tries to attach the right
**physical unit** as a semantic annotation. You don't have to do
anything: open any timeseries-container detail page, and channels with
recognisable names like `tcp_x_mm`, `force_z_N`, `chamber_bar`,
`tc_chamber_1` show up with their unit already filled in.

This page explains when the inference works, when it doesn't, and how
to correct or supply a unit yourself.

---

## When the inference fires

The auto-inference runs once, the first time a channel is created. It
recognises three families of field names:

1. **A unit token at the end of the name** — `_mm`, `_mm_s`, `_um`,
   `_N`, `_Nm`, `_kN`, `_J`, `_K`, `_C`, `_degC`, `_deg`, `_bar`,
   `_psi`, `_g`, `_Pa`. The longest match wins, so `velocity_mm_s`
   resolves to `millimeter per second`, not `millimeter`.
2. **A domain prefix at the start of the name** — joint angles `j1_…j7_`,
   plus engineering shorthands (`acc_`, `rpm_`, `mdot_`, `vib_`) and
   rocket-engine conventions (`tc_`, `pc_`, `p_inj_`, `p_tank_`,
   `t_coolant_`, `t_lox_`, `lch4_temperature`, `turbopump_*`,
   `strain_`).
3. **Resistance-welding cap codes** — `CM_I`, `W1_U`, `W2_I`, `WC_U`,
   etc. Tail `_I` is read as Ampere, tail `_U` as Volt. Tails `_p`
   and `_t` are deliberately left blank because they could mean
   pressure-or-power and time-or-temperature respectively.

If your field name fits one of these patterns, you get a unit chip on
the channel for free.

---

## What if the unit is wrong, or not there at all?

A handful of field names are intentionally ambiguous to Shepard's rule
set — `BridgePosition`, `valve_fuel`, `valve_lox`, the cap-controller
`_p` / `_t` variants. For these, Shepard leaves the channel
un-annotated and logs a warning (you can find these in the operator
dashboard under "AI1v: AMBIGUOUS").

In every case, you can override or supply the unit by hand:

1. Open the timeseries-container detail page.
2. Click the channel in the per-channel list.
3. In the channel's annotation panel, click **+ Add unit**.
4. Pick a QUDT unit term from the search box (start typing
   "millimeter", "newton", "bar", …). The vocabulary is bundled with
   Shepard — there's no internet round-trip.
5. Save. The new annotation has `sourceMode: human`. The original
   auto-inferred one, if any, is replaced.

The same flow works for **bulk-correcting** a misnamed convention
across many channels at once via the **multi-select** affordance on
the per-channel list.

---

## Trust signals

Every auto-inferred unit annotation carries a **confidence** field
between 0 and 1:

| Tier               | Confidence | Example                              |
| ------------------ | ---------- | ------------------------------------ |
| Suffix match       | 1.00       | `tcp_x_mm` → millimeter              |
| Welding cap        | 0.90       | `CM_I` → ampere                      |
| Prefix heuristic   | 0.85       | `tc_chamber_1` → kelvin              |

In the UI, the chip's tooltip shows the tier and the confidence so you
can decide at a glance whether to trust it.

---

## What's coming next

The **Phase 2** of the unit inference (gated on the AI plugin) will
ask a language model to resolve the ambiguous tail — typically by
looking at the parent DataObject's process step, instrument family,
and any existing annotations. Until then, the ambiguous handful go
through the manual-correction flow above.

---

## See also

- [Semantic annotations (reference)](/reference/semantic-annotations/) §10b "Channel unit auto-inference"
- [Annotating data (task guide)](/help/annotating-data/)
- [Timeseries reference](/reference/timeseries-reference/)
