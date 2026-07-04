/**
 * PLUGIN-REF-HANDLER-FE-REPOINT — unit tests for the repointed
 * useFetchHdfReferences composable.
 *
 * Verifies that:
 *  - the composable calls GET /v2/references?kind=hdf&dataObjectAppId=...
 *  - payload fields are mapped correctly to HdfReferenceIO
 *  - 404 is treated as empty list (DataObject not found)
 *  - 400 is treated as empty list (kind handler not installed / HDF plugin off)
 *  - other API errors set fetchError and clear isLoading
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Override the default setup.ts useAuth stub to provide a valid access token.
(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: vi.fn().mockResolvedValue(undefined),
  data: ref<{ accessToken: string } | null>({ accessToken: "test-token" }),
  signIn: vi.fn().mockResolvedValue(undefined),
});

beforeEach(() => {
  vi.clearAllMocks();
});

const RAW_HDF_REF = {
  appId: "hdf-ref-001",
  kind: "hdf",
  payload: {
    hdfContainerAppId: "hdf-container-abc",
    datasetPath: "/sensor_data/channel_A",
    description: "Primary vibration channel",
  },
};

const RAW_HDF_REF_MINIMAL = {
  appId: "hdf-ref-002",
  kind: "hdf",
  payload: {},
};

function okResponse(body: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(""),
  } as Response);
}

function statusResponse(status: number, ok = false) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.reject(new Error("not json")),
    text: () => Promise.resolve(`HTTP ${status}`),
  } as unknown as Response);
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchHdfReferences", () => {
  it("calls the unified /v2/references?kind=hdf endpoint", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_HDF_REF]));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    useFetchHdfReferences("do-app-001");
    await flush();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/v2/references?kind=hdf&dataObjectAppId=do-app-001"),
      expect.objectContaining({ headers: expect.objectContaining({ Accept: "application/json" }) }),
    );
  });

  it("maps payload fields to HdfReferenceIO correctly", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_HDF_REF]));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(references.value).toHaveLength(1);
    const ref = references.value[0]!;
    expect(ref.appId).toBe("hdf-ref-001");
    expect(ref.hdfContainerAppId).toBe("hdf-container-abc");
    expect(ref.datasetPath).toBe("/sensor_data/channel_A");
    expect(ref.description).toBe("Primary vibration channel");
  });

  it("maps a minimal payload (empty payload fields)", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_HDF_REF_MINIMAL]));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(references.value).toHaveLength(1);
    const ref = references.value[0]!;
    expect(ref.appId).toBe("hdf-ref-002");
    expect(ref.hdfContainerAppId).toBeUndefined();
    expect(ref.datasetPath).toBeUndefined();
    expect(ref.description).toBeUndefined();
  });

  it("treats HTTP 404 as empty list (DataObject not found)", async () => {
    mockFetch.mockReturnValue(statusResponse(404));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references, isLoading } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(references.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });

  it("treats HTTP 400 as empty list (kind handler not installed / HDF plugin off)", async () => {
    mockFetch.mockReturnValue(statusResponse(400));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references, isLoading } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(references.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });

  it("BUG-DO-DETAIL-A-TOAST-2026-06-29 — unwraps the paged envelope { items: [...] }", async () => {
    mockFetch.mockReturnValue(okResponse({ items: [RAW_HDF_REF], totalElements: 1 }));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references, fetchError } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(fetchError.value).toBeNull();
    expect(references.value).toHaveLength(1);
    expect(references.value[0]!.appId).toBe("hdf-ref-001");
  });

  it("sets fetchError and clears isLoading on unexpected API error", async () => {
    mockFetch.mockReturnValue(statusResponse(500));

    const { useFetchHdfReferences } = await import(
      "~/composables/context/useFetchHdfReferences"
    );
    const { references, isLoading, fetchError } = useFetchHdfReferences("do-app-001");
    await flush();

    expect(references.value).toEqual([]);
    expect(isLoading.value).toBe(false);
    expect(fetchError.value).toContain("HTTP 500");
  });
});
