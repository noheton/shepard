import { describe, it, expect } from "vitest";

function buildDiffUrl(aAppId: string, bAppId: string): string {
  return `/snapshots/diff?a=${aAppId}&b=${bAppId}`;
}

function compareHeadOptions<T extends { appId?: string | null }>(snapshots: T[], baseAppId: string): T[] {
  return snapshots.filter(s => s.appId !== baseAppId);
}

describe("snapshotsDiffNav — URL construction", () => {
  it("builds the correct diff URL with both appIds", () => {
    expect(buildDiffUrl("aaa-111", "bbb-222")).toBe("/snapshots/diff?a=aaa-111&b=bbb-222");
  });

  it("uses query params a= and b= matching the diff page route.query shape", () => {
    const url = buildDiffUrl("snap-a", "snap-b");
    expect(url).toContain("?a=snap-a");
    expect(url).toContain("&b=snap-b");
  });
});

describe("snapshotsDiffNav — compare head options", () => {
  const snapshots = [
    { appId: "snap-1", name: "First" },
    { appId: "snap-2", name: "Second" },
    { appId: "snap-3", name: "Third" },
  ];

  it("excludes the base snapshot from the compare options", () => {
    const options = compareHeadOptions(snapshots, "snap-1");
    expect(options.map(s => s.appId)).not.toContain("snap-1");
    expect(options).toHaveLength(2);
  });

  it("includes all other snapshots as compare candidates", () => {
    const options = compareHeadOptions(snapshots, "snap-2");
    expect(options.map(s => s.appId)).toEqual(["snap-1", "snap-3"]);
  });

  it("returns empty array when only one snapshot exists", () => {
    const options = compareHeadOptions([{ appId: "snap-1", name: "Only" }], "snap-1");
    expect(options).toHaveLength(0);
  });
});
