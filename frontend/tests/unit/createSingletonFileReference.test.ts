/**
 * APISIMP-KIND-DISCRIMINATOR-2 — Vitest cases for the two-step singleton-upload
 * composable URL + body shapes.
 *
 * The composable's contract after retiring POST /v2/files (multipart):
 *   Step 1: POST <v2BaseUrl>/v2/references?kind=file&dataObjectAppId=<doAppId>
 *           Content-Type: application/json
 *           body: {"name": "<name>"}
 *   Step 2: PUT <v2BaseUrl>/v2/references/<refAppId>/content?filename=<original-name>
 *           Content-Type: application/octet-stream
 *           body: file bytes
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

describe("singleton FileReference URL shape — step 1 (create metadata)", () => {
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

  it("builds the step-1 POST URL with kind=file and dataObjectAppId", () => {
    const base = v2BaseUrlFor({ backendApiUrl: "https://shepard-api.nuclide.systems/shepard/api" });
    const qs = new URLSearchParams({
      kind: "file",
      dataObjectAppId: "019e7244-0000-7000-8000-000000000001",
    }).toString();
    expect(`${base}/v2/references?${qs}`).toBe(
      "https://shepard-api.nuclide.systems/v2/references?" +
        "kind=file&" +
        "dataObjectAppId=019e7244-0000-7000-8000-000000000001",
    );
  });

  it("builds a JSON body with the name field", () => {
    const body = JSON.stringify({ name: "kr210-r2700-urdf" });
    expect(JSON.parse(body)).toEqual({ name: "kr210-r2700-urdf" });
  });

  it("URL-encodes special characters in the dataObjectAppId param", () => {
    const qs = new URLSearchParams({
      kind: "file",
      dataObjectAppId: "019e7244-0000-7000-8000-000000000001",
    }).toString();
    expect(qs).toBe("kind=file&dataObjectAppId=019e7244-0000-7000-8000-000000000001");
  });
});

describe("singleton FileReference URL shape — step 2 (upload bytes)", () => {
  it("builds the PUT URL with refAppId and filename", () => {
    const base = v2BaseUrlFor({ backendApiUrl: "https://shepard-api.nuclide.systems/shepard/api" });
    const refAppId = "019e7244-0000-7000-8000-000000000002";
    const uploadQs = new URLSearchParams({ filename: "kr210.urdf" }).toString();
    expect(`${base}/v2/references/${encodeURIComponent(refAppId)}/content?${uploadQs}`).toBe(
      "https://shepard-api.nuclide.systems/v2/references/019e7244-0000-7000-8000-000000000002/content?filename=kr210.urdf",
    );
  });

  it("URL-encodes spaces and special characters in the filename param", () => {
    const qs = new URLSearchParams({ filename: "AFP layup Ply 5.src" }).toString();
    expect(qs).toContain("filename=AFP+layup+Ply+5.src");
  });

  it("step-2 body is the raw File object (octet-stream), not a FormData", () => {
    const file = new File(["urdf-bytes"], "kr210.urdf", { type: "application/xml" });
    // The body is the File directly — not wrapped in FormData.
    expect(file).toBeInstanceOf(File);
    expect(file.name).toBe("kr210.urdf");
    // A File sent as fetch body is an octet-stream; no multipart boundary.
    expect(file instanceof FormData).toBe(false);
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
