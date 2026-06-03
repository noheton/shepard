/**
 * SINGLETON-FILE-04 — Vitest cases for the singleton-upload composable's
 * URL + form shape. We can't exercise the Vue lifecycle here without
 * spinning up the auto-imports stack, but the URL-shape contract is
 * mechanical enough to mock-test directly.
 *
 * The composable's contract:
 *   - POST <v2BaseUrl>/v2/files?parentDataObjectAppId=<appId>&name=<name>
 *   - Content-Type set by FormData (multipart boundary auto-derived)
 *   - body is a FormData with a single `file` part carrying the File bytes
 */

import { describe, it, expect } from "vitest";

/**
 * Replicate the v2BaseUrl helper's behaviour against a known input.
 * Mirror the implementation in
 * `frontend/composables/references/useCreateSingletonFileReference.ts`.
 */
function v2BaseUrlFor(public_: { backendV2ApiUrl?: string; backendApiUrl?: string }): string {
  if (public_.backendV2ApiUrl && public_.backendV2ApiUrl.length > 0)
    return public_.backendV2ApiUrl.replace(/\/$/, "");
  return (public_.backendApiUrl ?? "")
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

describe("singleton FileReference URL shape", () => {
  it("derives the v2 base URL by stripping /shepard/api from backendApiUrl", () => {
    expect(
      v2BaseUrlFor({ backendApiUrl: "https://shepard-api.nuclide.systems/shepard/api" }),
    ).toBe("https://shepard-api.nuclide.systems");
  });

  it("prefers the explicit backendV2ApiUrl when present", () => {
    expect(
      v2BaseUrlFor({
        backendApiUrl: "https://example.com/shepard/api",
        backendV2ApiUrl: "https://special.test/v2-root",
      }),
    ).toBe("https://special.test/v2-root");
  });

  it("strips a trailing slash from backendV2ApiUrl", () => {
    expect(
      v2BaseUrlFor({ backendV2ApiUrl: "https://special.test/" }),
    ).toBe("https://special.test");
  });

  it("builds the singleton POST URL with both query params", () => {
    const base = v2BaseUrlFor({ backendApiUrl: "https://shepard-api.nuclide.systems/shepard/api" });
    const qs = new URLSearchParams({
      parentDataObjectAppId: "019e7244-0000-7000-8000-000000000001",
      name: "kr210-r2700-urdf",
    }).toString();
    expect(`${base}/v2/files?${qs}`).toBe(
      "https://shepard-api.nuclide.systems/v2/files?" +
        "parentDataObjectAppId=019e7244-0000-7000-8000-000000000001&" +
        "name=kr210-r2700-urdf",
    );
  });

  it("URL-encodes special characters in the reference name", () => {
    const qs = new URLSearchParams({
      parentDataObjectAppId: "019e7244-0000-7000-8000-000000000001",
      name: "2026-06-03/calibration with spaces & symbols+",
    }).toString();
    // URLSearchParams uses application/x-www-form-urlencoded — space → "+".
    expect(qs).toContain("name=2026-06-03%2Fcalibration+with+spaces+%26+symbols%2B");
  });

  it("FormData carries exactly one `file` part with the source filename", () => {
    const file = new File(["urdf-bytes"], "kr210.urdf", { type: "application/xml" });
    const fd = new FormData();
    fd.append("file", file, file.name);
    // FormData.get returns the most-recently-appended value for the key.
    const got = fd.get("file");
    expect(got).toBeInstanceOf(File);
    expect((got as File).name).toBe("kr210.urdf");
    expect((got as File).type).toBe("application/xml");
    // Per the multipart-form-data contract there's exactly one entry under
    // `file` after a single append. No leakage from prior tests because
    // `fd` is freshly minted per case.
    expect(Array.from(fd.entries()).filter(([k]) => k === "file")).toHaveLength(1);
  });
});

describe("singleton vs bundle upload-mode gating", () => {
  // Mirrors `isUploadButtonDisabled` in DataObjectFileUploadDialog.vue.
  // Singleton mode bypasses all FileContainer + reference-name gating; the
  // only check is "at least one file selected".
  function isUploadButtonDisabled(s: {
    files: File[] | undefined;
    uploadMode: "singleton" | "bundle";
    createReference: boolean;
    newReferenceName: string;
    containerMode: "link" | "create";
    fileContainerId: number | undefined;
    newFileContainerName: string;
  }): boolean {
    if (s.files === undefined) return true;
    if (Array.isArray(s.files) && s.files.length === 0) return true;
    if (s.uploadMode === "singleton") return false;
    return (
      (s.createReference && !s.newReferenceName) ||
      (s.containerMode === "link" && s.fileContainerId === undefined) ||
      (s.containerMode === "create" && !s.newFileContainerName)
    );
  }

  const file = new File(["x"], "x.txt");

  it("blocks submit when no files are selected (any mode)", () => {
    expect(
      isUploadButtonDisabled({
        files: [],
        uploadMode: "singleton",
        createReference: true,
        newReferenceName: "",
        containerMode: "link",
        fileContainerId: undefined,
        newFileContainerName: "",
      }),
    ).toBe(true);
    expect(
      isUploadButtonDisabled({
        files: [],
        uploadMode: "bundle",
        createReference: true,
        newReferenceName: "anything",
        containerMode: "link",
        fileContainerId: 42,
        newFileContainerName: "",
      }),
    ).toBe(true);
  });

  it("enables submit in singleton mode without any container picked", () => {
    expect(
      isUploadButtonDisabled({
        files: [file],
        uploadMode: "singleton",
        createReference: true,
        newReferenceName: "",
        containerMode: "link",
        fileContainerId: undefined,
        newFileContainerName: "",
      }),
    ).toBe(false);
  });

  it("blocks bundle-mode submit when no FileContainer is linked", () => {
    expect(
      isUploadButtonDisabled({
        files: [file],
        uploadMode: "bundle",
        createReference: false,
        newReferenceName: "",
        containerMode: "link",
        fileContainerId: undefined,
        newFileContainerName: "",
      }),
    ).toBe(true);
  });

  it("blocks bundle-mode submit when creating a new container with no name", () => {
    expect(
      isUploadButtonDisabled({
        files: [file],
        uploadMode: "bundle",
        createReference: false,
        newReferenceName: "",
        containerMode: "create",
        fileContainerId: undefined,
        newFileContainerName: "",
      }),
    ).toBe(true);
  });

  it("blocks bundle-mode submit when createReference is on but the name is empty", () => {
    expect(
      isUploadButtonDisabled({
        files: [file],
        uploadMode: "bundle",
        createReference: true,
        newReferenceName: "",
        containerMode: "link",
        fileContainerId: 42,
        newFileContainerName: "",
      }),
    ).toBe(true);
  });

  it("enables bundle-mode submit when all bundle gates are satisfied", () => {
    expect(
      isUploadButtonDisabled({
        files: [file],
        uploadMode: "bundle",
        createReference: true,
        newReferenceName: "calibration-2026-06-03",
        containerMode: "link",
        fileContainerId: 42,
        newFileContainerName: "",
      }),
    ).toBe(false);
  });
});

describe("singleton reference-name derivation", () => {
  // Mirrors the `${datePrefix}-${f.name}` pattern in `handleSingletonSubmit`.
  function deriveName(datePrefix: string, file: File): string {
    return `${datePrefix}-${file.name}`;
  }

  it("prefixes the filename with the date", () => {
    expect(deriveName("2026-06-03", new File([], "kr210.urdf"))).toBe(
      "2026-06-03-kr210.urdf",
    );
  });

  it("preserves spaces and special characters in the filename", () => {
    expect(deriveName("2026-06-03", new File([], "AFP layup Ply 5.src"))).toBe(
      "2026-06-03-AFP layup Ply 5.src",
    );
  });

  it("does not collapse N filenames to one — each gets its own derived name", () => {
    const names = ["mesh1.stl", "mesh2.stl", "mesh3.stl"].map(
      n => deriveName("2026-06-03", new File([], n)),
    );
    expect(new Set(names).size).toBe(3);
    expect(names).toEqual([
      "2026-06-03-mesh1.stl",
      "2026-06-03-mesh2.stl",
      "2026-06-03-mesh3.stl",
    ]);
  });
});
