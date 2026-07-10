/**
 * UIRULE-DROPDOWN-SEARCH-SORT — the URDF / .dat picker option builders used by
 * InterpretAsTrajectoryButton feed <v-autocomplete>s; their options must come
 * out in natural (numeric-aware) order so "robot2.urdf" precedes "robot10.urdf".
 */
import { describe, it, expect } from "vitest";
import {
  urdfPickerOptions,
  datPickerOptions,
} from "~/components/container/file/interpretAsTrajectoryHelpers";

const refs = [
  { name: "robot10.urdf", appId: "u10" },
  { name: "robot2.urdf", appId: "u2" },
  { name: "robot1.urdf", appId: "u1" },
  { name: "trajectory10.dat", appId: "d10" },
  { name: "trajectory2.dat", appId: "d2" },
  { name: "notes.txt", appId: "x" },
  { name: "missing.urdf", appId: null },
];

describe("urdfPickerOptions", () => {
  it("returns only .urdf refs with a value, in natural order", () => {
    expect(urdfPickerOptions(refs).map(o => o.title)).toEqual([
      "robot1.urdf",
      "robot2.urdf",
      "robot10.urdf",
    ]);
  });

  it("drops refs without an appId", () => {
    expect(urdfPickerOptions(refs).some(o => o.title === "missing.urdf")).toBe(false);
  });
});

describe("datPickerOptions", () => {
  it("returns only .dat refs, in natural order", () => {
    expect(datPickerOptions(refs).map(o => o.title)).toEqual([
      "trajectory2.dat",
      "trajectory10.dat",
    ]);
  });
});
