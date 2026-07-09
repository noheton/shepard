/**
 * APISIMP-STATS-PERKIND-COLLAPSE — useContainerCardinality unified stats fetch.
 *
 * Proves: (1) null/undefined appId skips the fetch; (2) all supported kinds build
 * the unified /v2/containers/{appId}/stats URL; (3) unsupported kinds return null
 * without fetching; (4) cardinalityLabel returns correct singular/plural forms.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import {
  useContainerCardinality,
  cardinalityLabel,
} from "~/composables/containers/useContainerCardinality";

const APP_ID = "01928eaa-1111-7000-9000-aaaaaaaaaaaa";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

vi.stubGlobal("useAuth", () => ({
  data: ref({ accessToken: "test-token" }),
}));

vi.stubGlobal("useRuntimeConfig", () => ({
  public: {
    backendV2ApiUrl: "https://example.test",
    backendApiUrl: "",
  },
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

function jsonOk(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useContainerCardinality — null/undefined appId guard", () => {
  it("does not fetch when appId is null", async () => {
    const { cardinality, isLoading } = useContainerCardinality(null, "FILE");
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(cardinality.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("does not fetch when appId is undefined", async () => {
    const { cardinality, isLoading } = useContainerCardinality(undefined, "TIMESERIES");
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(cardinality.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });
});

describe("useContainerCardinality — unsupported kind", () => {
  it("returns null without fetching for BASIC type", async () => {
    const { cardinality, isLoading } = useContainerCardinality(APP_ID, "BASIC");
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(cardinality.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });
});

describe("useContainerCardinality — FILE kind", () => {
  it("fetches from the unified containers stats URL using appId", async () => {
    fetchMock.mockResolvedValueOnce(jsonOk({ fileCount: 7 }));

    const { cardinality } = useContainerCardinality(APP_ID, "FILE");
    await flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toBe(`https://example.test/v2/containers/${APP_ID}/stats`);
    expect(cardinality.value).toBe(7);
  });

  it("returns 0 when fileCount is 0", async () => {
    fetchMock.mockResolvedValueOnce(jsonOk({ fileCount: 0 }));
    const { cardinality } = useContainerCardinality(APP_ID, "FILE");
    await flush();
    expect(cardinality.value).toBe(0);
  });
});

describe("useContainerCardinality — TIMESERIES kind", () => {
  it("fetches from the unified containers stats URL using appId", async () => {
    fetchMock.mockResolvedValueOnce(jsonOk({ channelCount: 12 }));

    const { cardinality } = useContainerCardinality(APP_ID, "TIMESERIES");
    await flush();

    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toBe(`https://example.test/v2/containers/${APP_ID}/stats`);
    expect(cardinality.value).toBe(12);
  });
});

describe("useContainerCardinality — STRUCTUREDDATA kind", () => {
  it("fetches from the unified containers stats URL using appId", async () => {
    fetchMock.mockResolvedValueOnce(jsonOk({ entryCount: 3 }));

    const { cardinality } = useContainerCardinality(APP_ID, "STRUCTUREDDATA");
    await flush();

    const url = fetchMock.mock.calls[0]![0] as string;
    expect(url).toBe(`https://example.test/v2/containers/${APP_ID}/stats`);
    expect(cardinality.value).toBe(3);
  });
});

describe("useContainerCardinality — error handling", () => {
  it("leaves cardinality null when fetch rejects", async () => {
    fetchMock.mockRejectedValueOnce(new Error("network failure"));
    const { cardinality, isLoading } = useContainerCardinality(APP_ID, "FILE");
    await flush();
    expect(cardinality.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("leaves cardinality null on non-2xx response", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 403 } as Response);
    const { cardinality, isLoading } = useContainerCardinality(APP_ID, "FILE");
    await flush();
    expect(cardinality.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });
});

describe("cardinalityLabel", () => {
  it("pluralises files correctly", () => {
    expect(cardinalityLabel("FILE", 0)).toBe("0 files");
    expect(cardinalityLabel("FILE", 1)).toBe("1 file");
    expect(cardinalityLabel("FILE", 2)).toBe("2 files");
  });

  it("pluralises channels correctly", () => {
    expect(cardinalityLabel("TIMESERIES", 1)).toBe("1 channel");
    expect(cardinalityLabel("TIMESERIES", 3)).toBe("3 channels");
  });

  it("pluralises entries correctly", () => {
    expect(cardinalityLabel("STRUCTUREDDATA", 1)).toBe("1 entry");
    expect(cardinalityLabel("STRUCTUREDDATA", 2)).toBe("2 entries");
  });
});
