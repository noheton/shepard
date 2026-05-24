import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

import {
  useFetchCollectionLabJournalEntries,
  groupByDataObjectId,
} from "~/composables/context/useFetchCollectionLabJournalEntries";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const mockList = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listCollectionLabJournalEntries: mockList }),
  );
});

/** Flush micro-task queue (watch({ immediate }) fires after the current tick). */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

const fakeEntry = (id: number, dataObjectId: number, content = "x"): unknown => ({
  id,
  dataObjectId,
  journalContent: content,
  createdAt: new Date(1_700_000_000_000 + id * 1000),
  createdBy: "alice",
  updatedAt: null,
  updatedBy: null,
});

describe("useFetchCollectionLabJournalEntries — single bulk fetch", () => {
  it("issues exactly one API call for the whole collection (the N+1 fix)", async () => {
    mockList.mockResolvedValue([]);
    useFetchCollectionLabJournalEntries(ref("collection-app-id-1"));
    await flush();
    expect(mockList).toHaveBeenCalledTimes(1);
    expect(mockList).toHaveBeenCalledWith({ collectionAppId: "collection-app-id-1" });
  });

  it("entries is undefined before the call resolves", () => {
    let resolveList!: (v: unknown[]) => void;
    mockList.mockReturnValue(new Promise(r => { resolveList = r; }));
    const { entries } = useFetchCollectionLabJournalEntries(ref("c-1"));
    // Pre-resolve: undefined.
    expect(entries.value).toBeUndefined();
    resolveList([]);
  });

  it("populates entries with the bulk response", async () => {
    const data = [fakeEntry(1, 101), fakeEntry(2, 102)];
    mockList.mockResolvedValue(data);
    const { entries } = useFetchCollectionLabJournalEntries(ref("c-1"));
    await flush();
    expect(entries.value).toHaveLength(2);
  });

  it("falls back to empty array on API error (no crash)", async () => {
    mockList.mockRejectedValue(Object.assign(new Error("boom"), { status: 500 }));
    const { entries } = useFetchCollectionLabJournalEntries(ref("c-1"));
    await flush();
    expect(entries.value).toEqual([]);
  });

  it("skips the fetch when collectionAppId is null", async () => {
    const { entries } = useFetchCollectionLabJournalEntries(ref<string | null>(null));
    await flush();
    expect(mockList).not.toHaveBeenCalled();
    expect(entries.value).toBeUndefined();
  });

  it("re-fetches when collectionAppId ref changes", async () => {
    mockList.mockResolvedValue([]);
    const appId = ref<string | null>("c-1");
    useFetchCollectionLabJournalEntries(appId);
    await flush();
    expect(mockList).toHaveBeenCalledTimes(1);

    appId.value = "c-2";
    await flush();
    expect(mockList).toHaveBeenCalledTimes(2);
    expect(mockList).toHaveBeenLastCalledWith({ collectionAppId: "c-2" });
  });
});

describe("groupByDataObjectId", () => {
  it("groups multiple entries under the same dataObjectId", () => {
    const a = fakeEntry(1, 100) as { dataObjectId: number };
    const b = fakeEntry(2, 100) as { dataObjectId: number };
    const c = fakeEntry(3, 200) as { dataObjectId: number };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const grouped = groupByDataObjectId([a, b, c] as any);
    expect(grouped.get(100)).toHaveLength(2);
    expect(grouped.get(200)).toHaveLength(1);
  });

  it("returns an empty map for empty input", () => {
    const grouped = groupByDataObjectId([]);
    expect(grouped.size).toBe(0);
  });

  it("preserves input order within a group (createdAt DESC from server)", () => {
    const newer = fakeEntry(11, 50) as { id: number; dataObjectId: number };
    const older = fakeEntry(7, 50) as { id: number; dataObjectId: number };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const grouped = groupByDataObjectId([newer, older] as any);
    const bucket = grouped.get(50);
    expect(bucket).toBeDefined();
    expect(bucket![0]).toEqual(newer);
    expect(bucket![1]).toEqual(older);
  });
});
