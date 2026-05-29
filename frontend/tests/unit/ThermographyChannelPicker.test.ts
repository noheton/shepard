/**
 * ThermographyChannelPicker — unit tests for the pure-helper projection.
 *
 * Component is NOT mounted (matches the project's Vitest pattern, see
 * UrdfChannelPicker.test.ts + Trace3DChannelPicker.test.ts). All logic
 * lives in `utils/thermographyChannelPicker.ts`.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16).
 */
import { describe, it, expect } from "vitest";
import {
  type AnnotationMap,
  THERMOGRAPHY_PREDICATES,
  MFFD_GRID_PREDICATES,
  extractGridPosition,
  formatGridPosition,
  hasGridPosition,
  projectMetadataRows,
  groupMetadataRows,
} from "~/utils/thermographyChannelPicker";

// ── predicate constants ──────────────────────────────────────────────────────

describe("THERMOGRAPHY_PREDICATES", () => {
  it("uses the urn:shepard:thermography:* namespace per the predicate-namespace rule", () => {
    expect(THERMOGRAPHY_PREDICATES.frameRateHz).toBe("urn:shepard:thermography:frameRate_Hz");
    expect(THERMOGRAPHY_PREDICATES.integrationTimeS).toBe("urn:shepard:thermography:integrationTime_s");
    expect(THERMOGRAPHY_PREDICATES.excitationDevice).toBe("urn:shepard:thermography:excitationDevice");
    expect(THERMOGRAPHY_PREDICATES.resolution).toBe("urn:shepard:thermography:resolution");
  });
});

describe("MFFD_GRID_PREDICATES", () => {
  it("uses the urn:shepard:mffd:* namespace", () => {
    expect(MFFD_GRID_PREDICATES.section).toBe("urn:shepard:mffd:section");
    expect(MFFD_GRID_PREDICATES.module).toBe("urn:shepard:mffd:module");
    expect(MFFD_GRID_PREDICATES.layer).toBe("urn:shepard:mffd:layer");
    expect(MFFD_GRID_PREDICATES.frame).toBe("urn:shepard:mffd:frame");
  });
});

// ── extractGridPosition / formatGridPosition / hasGridPosition ───────────────

describe("extractGridPosition", () => {
  it("returns all four fields when present", () => {
    const a: AnnotationMap = {
      [MFFD_GRID_PREDICATES.section]: "S4",
      [MFFD_GRID_PREDICATES.module]:  "M13",
      [MFFD_GRID_PREDICATES.layer]:   "L18",
      [MFFD_GRID_PREDICATES.frame]:   "F4",
    };
    expect(extractGridPosition(a)).toEqual({
      section: "S4", module: "M13", layer: "L18", frame: "F4",
    });
  });

  it("returns null for missing fields", () => {
    expect(extractGridPosition({})).toEqual({ section: null, module: null, layer: null, frame: null });
  });

  it("returns nulls when only some fields present", () => {
    const a: AnnotationMap = {
      [MFFD_GRID_PREDICATES.section]: "S1",
      [MFFD_GRID_PREDICATES.frame]:   "F2",
    };
    expect(extractGridPosition(a)).toEqual({
      section: "S1", module: null, layer: null, frame: "F2",
    });
  });
});

describe("formatGridPosition", () => {
  it("joins all-present positions with bullets", () => {
    expect(formatGridPosition({ section: "S4", module: "M13", layer: "L18", frame: "F4" }))
      .toBe("S4 · M13 · L18 · F4");
  });

  it("renders dash for empty positions", () => {
    expect(formatGridPosition({ section: null, module: null, layer: null, frame: null })).toBe("—");
  });

  it("skips null slots", () => {
    expect(formatGridPosition({ section: "S1", module: null, layer: "L3", frame: null }))
      .toBe("S1 · L3");
  });
});

describe("hasGridPosition", () => {
  it("is true when any grid predicate present", () => {
    expect(hasGridPosition({ [MFFD_GRID_PREDICATES.section]: "S1" })).toBe(true);
  });
  it("is false when no grid predicates present", () => {
    expect(hasGridPosition({ [THERMOGRAPHY_PREDICATES.frameRateHz]: "30" })).toBe(false);
    expect(hasGridPosition({})).toBe(false);
  });
});

// ── projectMetadataRows ──────────────────────────────────────────────────────

describe("projectMetadataRows", () => {
  it("returns an empty array when no annotations match", () => {
    expect(projectMetadataRows({})).toEqual([]);
    expect(projectMetadataRows({ "some:other:pred": "value" })).toEqual([]);
  });

  it("includes a row for each present predicate", () => {
    const a: AnnotationMap = {
      [THERMOGRAPHY_PREDICATES.frameRateHz]: "30",
      [THERMOGRAPHY_PREDICATES.resolution]:  "1024x768",
      [MFFD_GRID_PREDICATES.section]:        "S4",
    };
    const rows = projectMetadataRows(a);
    expect(rows.length).toBe(3);
    const labels = rows.map(r => r.label);
    expect(labels).toContain("Frame rate");
    expect(labels).toContain("Resolution");
    expect(labels).toContain("Section");
  });

  it("appends the unit suffix on numeric fields", () => {
    const a: AnnotationMap = {
      [THERMOGRAPHY_PREDICATES.frameRateHz]:           "30",
      [THERMOGRAPHY_PREDICATES.integrationTimeS]:      "0.007",
      [THERMOGRAPHY_PREDICATES.excitationAmplitudePct]: "70.00",
      [THERMOGRAPHY_PREDICATES.excitationFrequencyHz]: "0.015",
    };
    const rows = projectMetadataRows(a);
    const byLabel = Object.fromEntries(rows.map(r => [r.label, r.value]));
    expect(byLabel["Frame rate"]).toBe("30 Hz");
    expect(byLabel["Integration time"]).toBe("0.007 s");
    expect(byLabel["Excitation amplitude"]).toBe("70.00 %");
    expect(byLabel["Excitation frequency"]).toBe("0.015 Hz");
  });

  it("groups rows in the expected order: grid -> acquisition -> excitation -> provenance", () => {
    const a: AnnotationMap = {
      [MFFD_GRID_PREDICATES.section]:                  "S4",
      [THERMOGRAPHY_PREDICATES.resolution]:            "1024x768",
      [THERMOGRAPHY_PREDICATES.excitationDevice]:      "halogen",
      [THERMOGRAPHY_PREDICATES.campaign]:              "MFFD",
    };
    const rows = projectMetadataRows(a);
    expect(rows.map(r => r.group)).toEqual(["grid", "acquisition", "excitation", "provenance"]);
  });

  it("preserves predicate IRI on each row for debugging / advanced mode", () => {
    const a: AnnotationMap = { [THERMOGRAPHY_PREDICATES.frameRateHz]: "30" };
    expect(projectMetadataRows(a)[0]!.predicate).toBe(THERMOGRAPHY_PREDICATES.frameRateHz);
  });

  it("ignores blank string values", () => {
    const a: AnnotationMap = {
      [THERMOGRAPHY_PREDICATES.frameRateHz]: "",
      [MFFD_GRID_PREDICATES.section]:        "S4",
    };
    const rows = projectMetadataRows(a);
    expect(rows.length).toBe(1);
    expect(rows[0]!.label).toBe("Section");
  });
});

// ── groupMetadataRows ────────────────────────────────────────────────────────

describe("groupMetadataRows", () => {
  it("buckets rows by group key", () => {
    const a: AnnotationMap = {
      [MFFD_GRID_PREDICATES.section]:           "S4",
      [MFFD_GRID_PREDICATES.module]:            "M13",
      [THERMOGRAPHY_PREDICATES.frameRateHz]:    "30",
      [THERMOGRAPHY_PREDICATES.excitationDevice]: "halogen",
      [THERMOGRAPHY_PREDICATES.campaign]:       "MFFD",
    };
    const groups = groupMetadataRows(projectMetadataRows(a));
    expect(groups.grid.length).toBe(2);
    expect(groups.acquisition.length).toBe(1);
    expect(groups.excitation.length).toBe(1);
    expect(groups.provenance.length).toBe(1);
  });

  it("returns empty arrays for absent groups", () => {
    const groups = groupMetadataRows(projectMetadataRows({}));
    expect(groups.grid).toEqual([]);
    expect(groups.acquisition).toEqual([]);
    expect(groups.excitation).toEqual([]);
    expect(groups.provenance).toEqual([]);
  });
});
