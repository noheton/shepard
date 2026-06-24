/**
 * ThermographyCanvas — module-import + helper-projection sanity tests.
 *
 * The Vue component itself wraps Three.js and is impractical to mount in
 * a node test environment (Vitest config uses `environment: "node"`).
 * Mirrors the {@link ./UrdfCanvas.test.ts} pattern: test only the pure
 * helpers that prepare data for the canvas, and confirm the predicate
 * IRIs the canvas's parent view will receive are stable.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16).
 */
import { describe, it, expect } from "vitest";
import {
  THERMOGRAPHY_PREDICATES,
  MFFD_GRID_PREDICATES,
  projectMetadataRows,
  parseAspectRatio,
  type AnnotationMap,
} from "~/utils/thermographyChannelPicker";

describe("ThermographyCanvas integration surface", () => {
  it("projects the full real-fixture annotation set to a grouped metadata table", () => {
    // The annotation map below mirrors the output the OTvis backend
    // parser emits for the real `sample_S4_M13_L18_F4.OTvis` fixture
    // (see plugins/fileformat-thermography/src/test/.../OTvisParserTest.java).
    const fixture: AnnotationMap = {
      [MFFD_GRID_PREDICATES.section]:                   "S4",
      [MFFD_GRID_PREDICATES.module]:                    "M13",
      [MFFD_GRID_PREDICATES.layer]:                     "L18",
      [MFFD_GRID_PREDICATES.frame]:                     "F4",
      [THERMOGRAPHY_PREDICATES.frameRateHz]:            "30",
      [THERMOGRAPHY_PREDICATES.integrationTimeS]:       "0.007",
      [THERMOGRAPHY_PREDICATES.excitationDevice]:       "halogen",
      [THERMOGRAPHY_PREDICATES.excitationFrequencyHz]:  "0.015",
      [THERMOGRAPHY_PREDICATES.excitationAmplitudePct]: "70.00",
      [THERMOGRAPHY_PREDICATES.excitationSignalType]:   "sine",
      [THERMOGRAPHY_PREDICATES.recordingType]:          "evaluation",
      [THERMOGRAPHY_PREDICATES.resolution]:             "1024x768",
      [THERMOGRAPHY_PREDICATES.conditioningPeriods]:    "1",
      [THERMOGRAPHY_PREDICATES.acquisitionPeriods]:     "2",
      [THERMOGRAPHY_PREDICATES.campaign]:               "MFFD",
      [THERMOGRAPHY_PREDICATES.moduleName]:             "OTvis",
      [THERMOGRAPHY_PREDICATES.creatingVersion]:        "7.0.425.8903",
      [THERMOGRAPHY_PREDICATES.createdAt]:              "2023-07-02T06:55:41.414Z",
    };
    const rows = projectMetadataRows(fixture);
    expect(rows.length).toBeGreaterThanOrEqual(15);
    // Confirm the canvas's parent view will receive the rendering-relevant
    // fields it needs for aspect-ratio + label.
    const resolution = rows.find(r => r.label === "Resolution");
    expect(resolution?.value).toBe("1024x768");
  });

  it("renders no rows for an empty annotation map (canvas shows placeholder only)", () => {
    expect(projectMetadataRows({}).length).toBe(0);
  });
});

describe("parseAspectRatio", () => {
  it("parses standard OTvis 1024x768 resolution", () => {
    expect(parseAspectRatio("1024x768")).toBeCloseTo(1024 / 768, 5);
  });
  it("parses landscape HD 1920x1080", () => {
    expect(parseAspectRatio("1920x1080")).toBeCloseTo(1920 / 1080, 5);
  });
  it("accepts uppercase X separator", () => {
    expect(parseAspectRatio("640X480")).toBeCloseTo(640 / 480, 5);
  });
  it("accepts Unicode × separator", () => {
    expect(parseAspectRatio("800×600")).toBeCloseTo(800 / 600, 5);
  });
  it("falls back to 1024/768 for null/undefined/empty", () => {
    expect(parseAspectRatio(null)).toBeCloseTo(1024 / 768, 5);
    expect(parseAspectRatio(undefined)).toBeCloseTo(1024 / 768, 5);
    expect(parseAspectRatio("")).toBeCloseTo(1024 / 768, 5);
  });
  it("falls back to 1024/768 for non-parseable strings", () => {
    expect(parseAspectRatio("bad")).toBeCloseTo(1024 / 768, 5);
    expect(parseAspectRatio("0x0")).toBeCloseTo(1024 / 768, 5); // h=0 guard
  });
});
