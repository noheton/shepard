import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchAdminActivities } from "~/composables/context/admin/useFetchAdminActivities";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListActivities = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listActivities: mockListActivities }),
  );
});

/** Flush micro-task queue so auto-load on construction settles. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

function makeActivity(n: number) {
  return {
    appId: `act-${n}`,
    actionKind: "CREATE",
    agentUsername: `user${n}`,
    targetKind: "Collection",
    targetAppId: `col-${n}`,
    summary: `Created collection ${n}`,
    startedAtMillis: Date.now() - n * 1000,
    endedAtMillis: Date.now() - n * 900,
    method: "POST",
    path: `/v2/collections`,
    status: 201,
    originInstance: "test",
  };
}

function makePaged(items: ReturnType<typeof makeActivity>[]) {
  return { items, total: items.length, page: 0, pageSize: items.length };
}

describe("useFetchAdminActivities — initial load", () => {
  it("calls listActivities on construction with default limit=100", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    useFetchAdminActivities();
    await flush();
    expect(mockListActivities).toHaveBeenCalledTimes(1);
    expect(mockListActivities).toHaveBeenCalledWith(
      expect.objectContaining({ limit: 100 }),
    );
  });

  it("populates activities on successful response", async () => {
    const data = [makeActivity(1), makeActivity(2)];
    mockListActivities.mockResolvedValue(makePaged(data));
    const { activities } = useFetchAdminActivities();
    await flush();
    expect(activities.value).toHaveLength(2);
    expect(activities.value[0]?.appId).toBe("act-1");
  });

  it("sets hasMore=true when response length equals limit", async () => {
    // 100 rows returned = exactly the batch size → assume more exist
    const data = Array.from({ length: 100 }, (_, i) => makeActivity(i));
    mockListActivities.mockResolvedValue(makePaged(data));
    const { hasMore } = useFetchAdminActivities();
    await flush();
    expect(hasMore.value).toBe(true);
  });

  it("sets hasMore=false when response length is below limit", async () => {
    mockListActivities.mockResolvedValue(makePaged([makeActivity(1)]));
    const { hasMore } = useFetchAdminActivities();
    await flush();
    expect(hasMore.value).toBe(false);
  });

  it("resets activities to [] on API error (no crash)", async () => {
    mockListActivities.mockRejectedValue(new Error("Network error"));
    const { activities } = useFetchAdminActivities();
    await flush();
    expect(activities.value).toEqual([]);
  });

  it("isLoading starts true and resets to false after resolve", async () => {
    let resolve!: (v: object) => void;
    mockListActivities.mockReturnValue(new Promise(r => { resolve = r; }));
    const { isLoading } = useFetchAdminActivities();
    expect(isLoading.value).toBe(true);
    resolve(makePaged([]));
    await flush();
    expect(isLoading.value).toBe(false);
  });
});

describe("useFetchAdminActivities — filter params passed to API", () => {
  it("passes agent filter to listActivities when set", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { filterAgent, applyFilters } = useFetchAdminActivities();
    await flush(); // settle initial load

    filterAgent.value = "alice";
    await applyFilters();

    const lastCall = mockListActivities.mock.calls.at(-1)?.[0];
    expect(lastCall).toMatchObject({ agent: "alice" });
  });

  it("passes targetKind filter to listActivities when set", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { filterTargetKind, applyFilters } = useFetchAdminActivities();
    await flush();

    filterTargetKind.value = "DataObject";
    await applyFilters();

    const lastCall = mockListActivities.mock.calls.at(-1)?.[0];
    expect(lastCall).toMatchObject({ targetKind: "DataObject" });
  });

  it("passes targetAppId filter to listActivities when set", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { filterTargetAppId, applyFilters } = useFetchAdminActivities();
    await flush();

    filterTargetAppId.value = "do-uuid-123";
    await applyFilters();

    const lastCall = mockListActivities.mock.calls.at(-1)?.[0];
    expect(lastCall).toMatchObject({ targetAppId: "do-uuid-123" });
  });

  it("omits filter params that are empty strings", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { filterAgent, applyFilters } = useFetchAdminActivities();
    await flush();

    filterAgent.value = "";
    await applyFilters();

    const lastCall = mockListActivities.mock.calls.at(-1)?.[0];
    // agent must be undefined (not passed) when empty
    expect(lastCall.agent).toBeUndefined();
  });

  it("applyFilters resets limit to 100 before re-fetching", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { loadMore, applyFilters } = useFetchAdminActivities();
    await flush();

    // bump limit to 200
    await loadMore();
    let lastLimit = mockListActivities.mock.calls.at(-1)?.[0].limit;
    expect(lastLimit).toBe(200);

    // apply filters should reset to 100
    await applyFilters();
    lastLimit = mockListActivities.mock.calls.at(-1)?.[0].limit;
    expect(lastLimit).toBe(100);
  });
});

describe("useFetchAdminActivities — loadMore", () => {
  it("bumps limit by 100 on each loadMore call", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { loadMore } = useFetchAdminActivities();
    await flush();

    await loadMore();
    let lastLimit = mockListActivities.mock.calls.at(-1)?.[0].limit;
    expect(lastLimit).toBe(200);

    await loadMore();
    lastLimit = mockListActivities.mock.calls.at(-1)?.[0].limit;
    expect(lastLimit).toBe(300);
  });
});

describe("useFetchAdminActivities — resetFilters", () => {
  it("clears all three filter refs and re-fetches with limit=100", async () => {
    mockListActivities.mockResolvedValue(makePaged([]));
    const { filterAgent, filterTargetKind, filterTargetAppId, resetFilters } =
      useFetchAdminActivities();
    await flush();

    filterAgent.value = "bob";
    filterTargetKind.value = "FileReference";
    filterTargetAppId.value = "ref-456";

    await resetFilters();

    expect(filterAgent.value).toBe("");
    expect(filterTargetKind.value).toBe("");
    expect(filterTargetAppId.value).toBe("");

    const lastCall = mockListActivities.mock.calls.at(-1)?.[0];
    expect(lastCall.agent).toBeUndefined();
    expect(lastCall.targetKind).toBeUndefined();
    expect(lastCall.targetAppId).toBeUndefined();
    expect(lastCall.limit).toBe(100);
  });
});
