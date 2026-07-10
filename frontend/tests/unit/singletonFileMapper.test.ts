import { describe, it, expect, vi } from "vitest";
import {
  isIpynbFilename,
  isInlineVideoFileRow,
  mapSingletonFileReferenceToDataTableElement,
} from "~/components/context/display-components/data-references/dataTableElementMappingUtil";
import type { SingletonFileReferenceIO } from "~/composables/context/useFetchSingletonFileReferences";
import type { DataTableElement } from "~/components/context/display-components/data-references/dataTableElement";

// MP4-PROMOTE-VIDEO: the mapper now routes fileKind="video" rows to the
// file-reference detail page, so it reads the Nuxt auto-imported
// `fileReferencesPathFragment` global at call time — stub it here.
vi.stubGlobal("fileReferencesPathFragment", "/filereferences/");

const baseRef = (overrides: Partial<SingletonFileReferenceIO> = {}): SingletonFileReferenceIO => ({
  appId: "019e7244-0000-7000-8000-000000000001",
  name: "Calibration run",
  dataObjectId: 4039,
  createdAt: "2026-05-29T10:00:00Z",
  createdBy: "alice",
  type: "FileReference",
  file: {
    filename: "calibration.tif",
    fileSize: 12345,
    md5: "deadbeef",
    oid: "file-oid-1",
  } as SingletonFileReferenceIO["file"],
  ...overrides,
});

describe("isIpynbFilename", () => {
  it.each([
    ["notebook.ipynb", true],
    ["Analysis.IPYNB", true],
    ["nested.thing.ipynb", true],
    ["calibration.tif", false],
    ["readme.md", false],
    ["", false],
    [undefined, false],
    [null, false],
  ])("classifies %p as ipynb=%s", (filename, expected) => {
    expect(isIpynbFilename(filename as string | undefined | null)).toBe(expected);
  });
});

describe("mapSingletonFileReferenceToDataTableElement", () => {
  it("classifies a .tif as 'File' kind", () => {
    const row = mapSingletonFileReferenceToDataTableElement(baseRef());
    expect(row.type).toBe("File");
    expect(row.meta.appId).toBe("019e7244-0000-7000-8000-000000000001");
    expect(row.meta.filename).toBe("calibration.tif");
    expect(row.meta.fileSize).toBe(12345);
  });

  it("classifies a .ipynb as 'Notebook' kind", () => {
    const row = mapSingletonFileReferenceToDataTableElement(
      baseRef({
        file: {
          filename: "analysis.ipynb",
          fileSize: 8000,
          md5: "abc",
          oid: "o",
        } as SingletonFileReferenceIO["file"],
      }),
    );
    expect(row.type).toBe("Notebook");
    expect(row.meta.filename).toBe("analysis.ipynb");
  });

  it("uses the reference name in the row name field, not the filename", () => {
    const row = mapSingletonFileReferenceToDataTableElement(
      baseRef({ name: "TR-004 calibration" }),
    );
    expect(row.name).toBe("TR-004 calibration");
  });

  it("plumbs createdAt + createdBy through", () => {
    const row = mapSingletonFileReferenceToDataTableElement(baseRef());
    expect(row.created.createdBy).toBe("alice");
    expect(row.created.createdAt).toBeInstanceOf(Date);
    expect(row.created.createdAt.getUTCFullYear()).toBe(2026);
  });

  it("handles a missing file object", () => {
    const row = mapSingletonFileReferenceToDataTableElement(
      baseRef({ file: null }),
    );
    expect(row.type).toBe("File");
    expect(row.meta.filename).toBeUndefined();
    expect(row.meta.fileSize).toBeNull();
  });

  it("disables the legacy detail-page navigation (no numeric id)", () => {
    const row = mapSingletonFileReferenceToDataTableElement(baseRef());
    expect(row.actions.showDetails.enabled).toBe(false);
    expect(row.actions.elementAppId).toBe(row.meta.appId);
  });

  it("falls back to epoch date when createdAt is missing", () => {
    const row = mapSingletonFileReferenceToDataTableElement(
      baseRef({ createdAt: "" }),
    );
    expect(row.created.createdAt.valueOf()).toBe(0);
  });

  // ── MP4-PROMOTE-VIDEO ──────────────────────────────────────────────────────

  it("carries fileKind into the row meta (null when absent)", () => {
    const noKind = mapSingletonFileReferenceToDataTableElement(baseRef());
    expect(noKind.meta.fileKind).toBeNull();
    const pdf = mapSingletonFileReferenceToDataTableElement(
      baseRef({ fileKind: "pdf" }),
    );
    expect(pdf.meta.fileKind).toBe("pdf");
  });

  it("treats a fileKind=video singleton as a playable File row that opens to the detail page", () => {
    const row = mapSingletonFileReferenceToDataTableElement(
      baseRef({ name: "P01_2.Bahn.MP4", fileKind: "video" }),
    );
    // Stays a "File" row (NOT the separate VideoStreamReference "Video" tab)
    expect(row.type).toBe("File");
    expect(row.meta.fileKind).toBe("video");
    // ...but now navigates to the file-reference detail page (inline player)
    expect(row.actions.showDetails.enabled).toBe(true);
    expect(row.actions.showDetails.pathFragment).toBe("/filereferences/");
    expect(row.actions.elementAppId).toBe(row.meta.appId);
  });

  it("leaves non-video File singletons non-clickable (pre-existing behaviour)", () => {
    const pdf = mapSingletonFileReferenceToDataTableElement(
      baseRef({ fileKind: "pdf" }),
    );
    expect(pdf.actions.showDetails.enabled).toBe(false);
    const plain = mapSingletonFileReferenceToDataTableElement(baseRef());
    expect(plain.actions.showDetails.enabled).toBe(false);
  });
});

describe("isInlineVideoFileRow (drives the table video icon)", () => {
  const row = (
    type: DataTableElement["type"],
    fileKind: string | null | undefined,
  ): DataTableElement =>
    ({ type, meta: { fileKind } }) as unknown as DataTableElement;

  it("is true for a File row tagged fileKind=video", () => {
    expect(isInlineVideoFileRow(row("File", "video"))).toBe(true);
  });

  it("is false for non-video File rows", () => {
    expect(isInlineVideoFileRow(row("File", "pdf"))).toBe(false);
    expect(isInlineVideoFileRow(row("File", null))).toBe(false);
    expect(isInlineVideoFileRow(row("File", undefined))).toBe(false);
  });

  it("is false for other row types even if meta.fileKind were video", () => {
    // The separate VideoStreamReference "Video" tab is a distinct kind.
    expect(isInlineVideoFileRow(row("Video", "video"))).toBe(false);
    expect(isInlineVideoFileRow(row("Notebook", "video"))).toBe(false);
  });
});
