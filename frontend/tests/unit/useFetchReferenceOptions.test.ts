import { describe, it, expect } from "vitest";
import {
  mapToReferenceOptions,
  mapAccessibleUrdfOptions,
  MAPPING_REFERENCE_KINDS,
  isUrdfName,
  type ReferenceOption,
  type AccessibleUrdfItem,
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

describe("mapAccessibleUrdfOptions (URDF-FILEREF-PICKER-SEARCHABLE)", () => {
  it("labels each option '<name> — <collection>' and keeps kind=file", () => {
    const items: AccessibleUrdfItem[] = [
      {
        appId: "ref-kr210",
        name: "kr210-r2700-urdf",
        dataObjectAppId: "do-A",
        collectionAppId: "coll-A",
        collectionName: "MFFD RDK → URDF Viewer Showcase",
      },
    ];
    const opts = mapAccessibleUrdfOptions(items);
    expect(opts).toHaveLength(1);
    expect(opts[0]).toEqual<ReferenceOption>({
      appId: "ref-kr210",
      label: "kr210-r2700-urdf — MFFD RDK → URDF Viewer Showcase",
      kind: "file",
    });
  });

  it("falls back to the bare name when the collection is unknown", () => {
    const opts = mapAccessibleUrdfOptions([{ appId: "r1", name: "arm.urdf" }]);
    expect(opts[0]!.label).toBe("arm.urdf");
  });

  it("skips entries without an appId", () => {
    const items: AccessibleUrdfItem[] = [
      { appId: "", name: "orphan" },
      { appId: "r-ok", name: "good.urdf", collectionName: "Lab" },
    ];
    const opts = mapAccessibleUrdfOptions(items);
    expect(opts).toHaveLength(1);
    expect(opts[0]!.appId).toBe("r-ok");
  });

  it("orders options in natural (numeric-aware) order, not lexicographic", () => {
    const items: AccessibleUrdfItem[] = [
      { appId: "a10", name: "kr210-10.urdf" },
      { appId: "a2", name: "kr210-2.urdf" },
      { appId: "a1", name: "kr210-1.urdf" },
    ];
    const labels = mapAccessibleUrdfOptions(items).map((o) => o.label);
    expect(labels).toEqual(["kr210-1.urdf", "kr210-2.urdf", "kr210-10.urdf"]);
  });

  it("returns an empty array for an empty input list", () => {
    expect(mapAccessibleUrdfOptions([])).toEqual([]);
  });
});
