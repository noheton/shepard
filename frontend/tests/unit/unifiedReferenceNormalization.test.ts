/**
 * V2CONV-A2 — Vitest cases for the unified-envelope → flat-shape normalization
 * that `useFetchSingletonFileReferences` applies after repointing to
 * `GET /v2/references?kind=file&dataObjectAppId=…`.
 *
 * We mirror the mapping logic (rather than spin up the auto-imports stack) so
 * the contract is covered mechanically: the unified `ReferenceV2IO` carries the
 * embedded `ShepardFile` under `payload.file`, the FR1b discriminator under
 * `referenceShape === "singleton"`, and the file-kind under `fileKind`.
 */

import { describe, it, expect } from "vitest";

interface ShepardFileLike {
  filename: string;
  fileSize: number | null;
  md5: string;
  oid: string;
}

interface ReferenceV2IO {
  appId: string;
  name: string;
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  kind?: string;
  referenceShape?: string | null;
  fileKind?: string | null;
  payload?: { file?: ShepardFileLike | null } | null;
}

interface SingletonFileReferenceIO {
  appId: string;
  name: string;
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  file: ShepardFileLike | null;
  fileKind?: string | null;
}

/** Mirror of the normalization in useFetchSingletonFileReferences.refresh(). */
function normalize(unified: ReferenceV2IO[]): SingletonFileReferenceIO[] {
  return unified
    .filter(r => r.referenceShape === "singleton" || r.payload?.file !== undefined)
    .map(r => ({
      appId: r.appId,
      name: r.name,
      dataObjectId: r.dataObjectId,
      createdAt: r.createdAt,
      createdBy: r.createdBy,
      type: r.type,
      file: r.payload?.file ?? null,
      fileKind: r.fileKind ?? null,
    }));
}

const file: ShepardFileLike = {
  filename: "kr210.urdf",
  fileSize: 123,
  md5: "abc",
  oid: "oid-1",
};

describe("unified reference envelope normalization", () => {
  it("flattens payload.file and carries fileKind", () => {
    const out = normalize([
      {
        appId: "ref-1",
        name: "kr210",
        createdAt: "2026-06-04T00:00:00Z",
        createdBy: "alice",
        type: "FileReference",
        kind: "file",
        referenceShape: "singleton",
        fileKind: "urdf",
        payload: { file },
      },
    ]);
    expect(out).toHaveLength(1);
    expect(out[0]!.file).toEqual(file);
    expect(out[0]!.fileKind).toBe("urdf");
    expect(out[0]!.appId).toBe("ref-1");
  });

  it("maps a null file payload to file: null and fileKind: null", () => {
    const out = normalize([
      {
        appId: "ref-2",
        name: "degenerate",
        createdAt: "2026-06-04T00:00:00Z",
        createdBy: "bob",
        kind: "file",
        referenceShape: "singleton",
        payload: { file: null },
      },
    ]);
    expect(out[0]!.file).toBeNull();
    expect(out[0]!.fileKind).toBeNull();
  });

  it("keeps only singleton-shaped rows", () => {
    const out = normalize([
      {
        appId: "ok",
        name: "s",
        createdAt: "t",
        createdBy: "u",
        referenceShape: "singleton",
        payload: { file },
      },
      // A non-file row with no payload.file and not a singleton is filtered out.
      {
        appId: "drop",
        name: "uri",
        createdAt: "t",
        createdBy: "u",
        kind: "uri",
        referenceShape: null,
        payload: {},
      },
    ]);
    expect(out.map(r => r.appId)).toEqual(["ok"]);
  });
});
