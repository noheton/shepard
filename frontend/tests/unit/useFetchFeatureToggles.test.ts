import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchFeatureToggles } from "~/composables/context/admin/useFetchFeatureToggles";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListFeatureToggles = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listFeatureToggles: mockListFeatureToggles }),
  );
});

const flush = () => new Promise<void>(r => setTimeout(r, 0));

function makeToggle(name: string, enabled: boolean) {
  return { name, enabled, description: `Toggle ${name}`, source: "config" };
}

describe("useFetchFeatureToggles", () => {
  it("calls listFeatureToggles on construction", async () => {
    mockListFeatureToggles.mockResolvedValue([]);
    useFetchFeatureToggles();
    await flush();
    expect(mockListFeatureToggles).toHaveBeenCalledTimes(1);
  });

  it("populates features array from plain array response", async () => {
    const data = [makeToggle("neo4j-full-text-search", true), makeToggle("experimental-ui", false)];
    mockListFeatureToggles.mockResolvedValue(data);
    const { features } = useFetchFeatureToggles();
    await flush();
    expect(features.value).toHaveLength(2);
    expect(features.value[0]?.name).toBe("neo4j-full-text-search");
    expect(features.value[1]?.enabled).toBe(false);
  });

  it("resets features to [] on API error", async () => {
    mockListFeatureToggles.mockRejectedValue(new Error("Network error"));
    const { features } = useFetchFeatureToggles();
    await flush();
    expect(features.value).toEqual([]);
  });

  it("isLoading starts true and resets to false after settle", async () => {
    let resolve!: (v: object[]) => void;
    mockListFeatureToggles.mockReturnValue(new Promise(r => { resolve = r; }));
    const { isLoading } = useFetchFeatureToggles();
    expect(isLoading.value).toBe(true);
    resolve([]);
    await flush();
    expect(isLoading.value).toBe(false);
  });

  it("refresh re-fetches and updates features", async () => {
    mockListFeatureToggles.mockResolvedValue([makeToggle("feat-a", true)]);
    const { features, refresh } = useFetchFeatureToggles();
    await flush();
    expect(features.value).toHaveLength(1);

    mockListFeatureToggles.mockResolvedValue([
      makeToggle("feat-a", true),
      makeToggle("feat-b", false),
    ]);
    await refresh();
    expect(features.value).toHaveLength(2);
  });
});
