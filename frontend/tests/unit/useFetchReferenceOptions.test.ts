import { describe, it, expect } from "vitest";
import {
  mapToReferenceOptions,
  MAPPING_REFERENCE_KINDS,
  isUrdfName,
  type ReferenceOption,
} from "~/composables/useFetchReferenceOptions";

describe("mapToReferenceOptions", () => {
  it("maps a list of refs to picker options with label = name (kind)", () => {
    const refs = [
      { appId: "ref-1", name: "my-krl-file" },
      { appId: "ref-2", name: "urdf-robot" },
    ];
    const opts = mapToReferenceOptions(refs, "file");
    expect(opts).toHaveLength(2);
    expect(opts[0]).toEqual<ReferenceOption>({
      appId: "ref-1",
      label: "my-krl-file (file)",
      kind: "file",
    });
    expect(opts[1]).toEqual<ReferenceOption>({
      appId: "ref-2",
      label: "urdf-robot (file)",
      kind: "file",
    });
  });

  it("falls back to appId in label when name is absent", () => {
    const refs = [{ appId: "ref-abc" }];
    const opts = mapToReferenceOptions(refs, "uri");
    expect(opts[0]!.label).toBe("ref-abc (uri)");
  });

  it("skips entries without an appId field", () => {
    const refs: { name: string; appId?: string }[] = [
      { name: "no-appid" },
      { appId: "ref-ok", name: "fine" },
    ];
    const opts = mapToReferenceOptions(refs, "git");
    expect(opts).toHaveLength(1);
    expect(opts[0]!.appId).toBe("ref-ok");
  });

  it("returns an empty array for an empty input list", () => {
    expect(mapToReferenceOptions([], "file")).toEqual([]);
  });

  it("preserves the kind string on every option", () => {
    const refs = [
      { appId: "a", name: "A" },
      { appId: "b", name: "B" },
    ];
    const opts = mapToReferenceOptions(refs, "timeseries");
    expect(opts.every((o) => o.kind === "timeseries")).toBe(true);
  });

  it("MAPPING_REFERENCE_KINDS contains file, uri, and git", () => {
    expect(MAPPING_REFERENCE_KINDS).toContain("file");
    expect(MAPPING_REFERENCE_KINDS).toContain("uri");
    expect(MAPPING_REFERENCE_KINDS).toContain("git");
    expect(MAPPING_REFERENCE_KINDS).toHaveLength(3);
  });
});

describe("isUrdfName (URDF-REF-PICKER filter)", () => {
  it("accepts names ending with .urdf (lowercase)", () => {
    expect(isUrdfName("kr210-r2700.urdf")).toBe(true);
    expect(isUrdfName("robot.urdf")).toBe(true);
  });

  it("accepts names ending with .URDF (uppercase)", () => {
    expect(isUrdfName("Robot.URDF")).toBe(true);
  });

  it("accepts names ending with .Urdf (mixed case)", () => {
    expect(isUrdfName("model.Urdf")).toBe(true);
  });

  it("rejects names with .urdf not at the end", () => {
    expect(isUrdfName("my.urdf.bak")).toBe(false);
    expect(isUrdfName("urdf-file.xml")).toBe(false);
  });

  it("rejects names without .urdf suffix", () => {
    expect(isUrdfName("mesh.dae")).toBe(false);
    expect(isUrdfName("robot.sdf")).toBe(false);
    expect(isUrdfName("")).toBe(false);
  });

  it("handles names that are just '.urdf'", () => {
    expect(isUrdfName(".urdf")).toBe(true);
  });
});
