/**
 * V2-SWEEP-004-4 — unit tests for interpretAsTrajectoryHelpers.
 *
 * The helpers accept Array<{name, appId}> which matches the v2 /v2/references
 * response shape directly (after the migration from v1 getAllFileReferences).
 */
import { describe, it, expect } from "vitest";
import {
  isKrlSrcFile,
  isUrScriptFile,
  isTrajectoryFormValid,
  urdfPickerOptions,
  datPickerOptions,
} from "../../components/container/file/interpretAsTrajectoryHelpers";

describe("isKrlSrcFile", () => {
  it("matches .src extension (case-insensitive)", () => {
    expect(isKrlSrcFile("robot.SRC")).toBe(true);
    expect(isKrlSrcFile("robot.src")).toBe(true);
  });

  it("matches .krl extension", () => {
    expect(isKrlSrcFile("move.krl")).toBe(true);
    expect(isKrlSrcFile("MOVE.KRL")).toBe(true);
  });

  it("does not match unrelated extensions", () => {
    expect(isKrlSrcFile("robot.urdf")).toBe(false);
    expect(isKrlSrcFile("robot.urscript")).toBe(false);
    expect(isKrlSrcFile(null)).toBe(false);
    expect(isKrlSrcFile(undefined)).toBe(false);
    expect(isKrlSrcFile("")).toBe(false);
  });
});

describe("isUrScriptFile", () => {
  it("matches .urscript extension (case-insensitive)", () => {
    expect(isUrScriptFile("prog.urscript")).toBe(true);
    expect(isUrScriptFile("PROG.URSCRIPT")).toBe(true);
  });

  it("matches .script extension", () => {
    expect(isUrScriptFile("main.script")).toBe(true);
  });

  it("does not match .src or .krl", () => {
    expect(isUrScriptFile("robot.src")).toBe(false);
    expect(isUrScriptFile("robot.krl")).toBe(false);
    expect(isUrScriptFile(null)).toBe(false);
  });
});

describe("isTrajectoryFormValid", () => {
  const base = {
    urdfFileAppId: "0197b6a2-aaaa-7000-8000-000000000001",
    targetDataObjectAppId: "0197b6a2-aaaa-7000-8000-000000000002",
    timeseriesContainerAppId: "0197b6a2-aaaa-7000-8000-000000000003",
  };

  it("returns true when all fields are populated", () => {
    expect(isTrajectoryFormValid(base)).toBe(true);
  });

  it("returns false when urdfFileAppId is null", () => {
    expect(isTrajectoryFormValid({ ...base, urdfFileAppId: null })).toBe(false);
  });

  it("returns false when urdfFileAppId is blank", () => {
    expect(isTrajectoryFormValid({ ...base, urdfFileAppId: "  " })).toBe(false);
  });

  it("returns false when targetDataObjectAppId is blank", () => {
    expect(
      isTrajectoryFormValid({ ...base, targetDataObjectAppId: "" }),
    ).toBe(false);
  });

  it("returns false when timeseriesContainerAppId is blank", () => {
    expect(
      isTrajectoryFormValid({ ...base, timeseriesContainerAppId: "  " }),
    ).toBe(false);
  });
});

// urdfPickerOptions and datPickerOptions accept Array<{name, appId}> — the
// same shape returned by GET /v2/references?kind=file (V2-SWEEP-004-4 migration).
describe("urdfPickerOptions", () => {
  const refs = [
    { name: "kr210.urdf", appId: "aaa-111" },
    { name: "robot.src", appId: "bbb-222" },
    { name: "scene.URDF", appId: "ccc-333" },
    { name: "no-appid.urdf", appId: null },
    { name: "no-appid2.urdf", appId: undefined },
  ];

  it("returns only .urdf entries with a non-empty appId", () => {
    const opts = urdfPickerOptions(refs);
    expect(opts).toHaveLength(2);
    expect(opts[0]).toEqual({ title: "kr210.urdf", value: "aaa-111" });
    expect(opts[1]).toEqual({ title: "scene.URDF", value: "ccc-333" });
  });

  it("returns empty list when no .urdf refs", () => {
    expect(urdfPickerOptions([{ name: "robot.src", appId: "x" }])).toHaveLength(0);
  });

  it("returns empty list for empty input", () => {
    expect(urdfPickerOptions([])).toHaveLength(0);
  });
});

describe("datPickerOptions", () => {
  const refs = [
    { name: "config.dat", appId: "ddd-444" },
    { name: "robot.src", appId: "eee-555" },
    { name: "EXTRA.DAT", appId: "fff-666" },
    { name: "no-appid.dat", appId: null },
  ];

  it("returns only .dat entries with a non-empty appId", () => {
    const opts = datPickerOptions(refs);
    expect(opts).toHaveLength(2);
    expect(opts[0]).toEqual({ title: "config.dat", value: "ddd-444" });
    expect(opts[1]).toEqual({ title: "EXTRA.DAT", value: "fff-666" });
  });

  it("returns empty list when no .dat refs", () => {
    expect(datPickerOptions([{ name: "robot.urdf", appId: "x" }])).toHaveLength(0);
  });
});
