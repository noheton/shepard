/**
 * KRL-INTERPRETER-06 — unit tests for RunKrlPreviewDialog helpers.
 *
 * Mirrors EditFileReferenceDialog.test.ts: pure helper functions are
 * extracted into a sibling .ts file (`runKrlPreviewHelpers.ts`) and tested
 * without mounting the full Nuxt / Vuetify component tree.
 */
import { describe, it, expect } from "vitest";
import {
  isKrlFormValid,
  parseSeedPose,
  buildKrlRequestBody,
  urdfPickerOptions,
  datPickerOptions,
  sameStemDatAppId,
} from "../../components/dialog/runKrlPreviewHelpers";

describe("RunKrlPreviewDialog — isKrlFormValid", () => {
  const base = {
    urdfFileAppId: "u-1",
    targetDataObjectAppId: "d-1",
    timeseriesContainerAppId: "c-1",
  };

  it("is valid when all three required fields are set", () => {
    expect(isKrlFormValid(base)).toBe(true);
  });

  it("is invalid when URDF appId is null", () => {
    expect(isKrlFormValid({ ...base, urdfFileAppId: null })).toBe(false);
  });

  it("is invalid when target DataObject appId is blank", () => {
    expect(isKrlFormValid({ ...base, targetDataObjectAppId: "" })).toBe(false);
    expect(isKrlFormValid({ ...base, targetDataObjectAppId: "   " })).toBe(false);
  });

  it("is invalid when TimeseriesContainer appId is blank", () => {
    expect(isKrlFormValid({ ...base, timeseriesContainerAppId: "" })).toBe(false);
  });
});

describe("RunKrlPreviewDialog — parseSeedPose", () => {
  it("parses a comma-separated joint vector", () => {
    expect(parseSeedPose("0, -1.57, 1.57, 0, 1.57, 0")).toEqual([
      0, -1.57, 1.57, 0, 1.57, 0,
    ]);
  });

  it("returns undefined for an empty string", () => {
    expect(parseSeedPose("")).toBeUndefined();
    expect(parseSeedPose("   ")).toBeUndefined();
  });

  it("returns undefined when any value is not a number", () => {
    expect(parseSeedPose("0, foo, 1.57")).toBeUndefined();
  });

  it("accepts whitespace-only separation", () => {
    expect(parseSeedPose("1 2 3")).toEqual([1, 2, 3]);
  });
});

describe("RunKrlPreviewDialog — buildKrlRequestBody", () => {
  const baseInput = {
    srcFileAppId: "src-1",
    urdfFileAppId: "urdf-1",
    targetDataObjectAppId: "do-1",
    timeseriesContainerAppId: "tsc-1",
    datFileAppIds: [] as string[],
    timeStep: 0.01,
    ikTolerance: 0.001,
    maxIterations: 300,
    useBaseFrame: false,
    useToolFrame: false,
    baseFrame: { x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 },
    toolFrame: { x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 },
    seedPoseRaw: "",
  };

  it("emits the required fields + options for the minimal case", () => {
    const body = buildKrlRequestBody(baseInput);
    expect(body.srcFileAppId).toBe("src-1");
    expect(body.urdfFileAppId).toBe("urdf-1");
    expect(body.timeStep).toBe(0.01);
    expect(body.options).toEqual({ ikTolerance: 0.001, maxIterations: 300 });
    expect(body.datFileAppIds).toBeUndefined();
    expect(body.baseFrame).toBeUndefined();
    expect(body.toolFrame).toBeUndefined();
    expect(body.seedPose).toBeUndefined();
  });

  it("includes datFileAppIds when set", () => {
    const body = buildKrlRequestBody({
      ...baseInput,
      datFileAppIds: ["dat-1", "dat-2"],
    });
    expect(body.datFileAppIds).toEqual(["dat-1", "dat-2"]);
  });

  it("includes base frame ONLY when the toggle is on", () => {
    const body = buildKrlRequestBody({
      ...baseInput,
      useBaseFrame: true,
      baseFrame: { x: 100, y: 0, z: 0, rx: 0, ry: 0, rz: 0 },
    });
    expect(body.baseFrame).toEqual({ x: 100, y: 0, z: 0, rx: 0, ry: 0, rz: 0 });
  });

  it("includes parsed seedPose when provided", () => {
    const body = buildKrlRequestBody({
      ...baseInput,
      seedPoseRaw: "0, -1.57, 1.57",
    });
    expect(body.seedPose).toEqual([0, -1.57, 1.57]);
  });
});

describe("RunKrlPreviewDialog — file-picker helpers", () => {
  const refs = [
    { name: "Ply_5_layup.src", appId: "a-1" },
    { name: "Ply_5_layup.dat", appId: "a-2" },
    { name: "KUKA_R20_MFZ.urdf", appId: "a-3" },
    { name: "other.urdf", appId: "a-4" },
    { name: "notes.md", appId: "a-5" },
    { name: "no-id.urdf", appId: null },
  ];

  it("urdfPickerOptions extracts only .urdf files with a valid appId", () => {
    const out = urdfPickerOptions(refs);
    expect(out).toEqual([
      { title: "KUKA_R20_MFZ.urdf", value: "a-3" },
      { title: "other.urdf", value: "a-4" },
    ]);
  });

  it("datPickerOptions extracts only .dat files", () => {
    const out = datPickerOptions(refs);
    expect(out).toEqual([{ title: "Ply_5_layup.dat", value: "a-2" }]);
  });

  it("sameStemDatAppId picks the .dat with the matching stem", () => {
    const dats = datPickerOptions(refs);
    expect(sameStemDatAppId("Ply_5_layup.src", dats)).toBe("a-2");
  });

  it("sameStemDatAppId returns null when no .dat matches", () => {
    const dats = datPickerOptions(refs);
    expect(sameStemDatAppId("OtherProgram.src", dats)).toBeNull();
  });
});
