/**
 * PLUGIN-REF-HANDLER-FE-REPOINT — unit tests for the repointed
 * useFetchVideoStreamReferences composable.
 *
 * Verifies that:
 *  - the composable calls GET /v2/references?kind=video&dataObjectAppId=...
 *  - payload fields are mapped correctly to VideoStreamReferenceIO
 *  - API errors leave references empty and clear isLoading
 *  - downloadUrl points to the unified /v2/references/{appId}/content path (APISIMP-VIDEO-STREAMREF-PATH)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Override the default setup.ts useAuth stub to provide a valid access token.
// Without this the composable bails early on the `!accessToken` guard.
(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: vi.fn().mockResolvedValue(undefined),
  data: ref<{ accessToken: string } | null>({ accessToken: "test-token" }),
  signIn: vi.fn().mockResolvedValue(undefined),
});

beforeEach(() => {
  vi.clearAllMocks();
});

/** Minimal ReferenceV2IO with video payload. */
const RAW_VIDEO_REF = {
  appId: "vid-ref-001",
  id: 1,
  name: "My Video",
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "alice",
  kind: "video",
  payload: {
    mimeType: "video/mp4",
    fileSizeBytes: 1048576,
    durationSeconds: 30.5,
    width: 1920,
    height: 1080,
    frameRate: 29.97,
    videoCodec: "h264",
    audioCodec: "aac",
    wallClockTimestamp: "2023-11-14T22:13:20Z",
  },
};

function okResponse(body: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(""),
  } as Response);
}

function errorResponse(status: number) {
  return Promise.resolve({
    ok: false,
    status,
    json: () => Promise.reject(new Error("not json")),
    text: () => Promise.resolve(`HTTP ${status}`),
  } as unknown as Response);
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchVideoStreamReferences", () => {
  it("calls the unified /v2/references?kind=video endpoint", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_VIDEO_REF]));

    const { useFetchVideoStreamReferences } = await import(
      "~/composables/context/useFetchVideoStreamReferences"
    );
    useFetchVideoStreamReferences("do-app-001");
    await flush();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/v2/references?kind=video&dataObjectAppId=do-app-001"),
      expect.objectContaining({ headers: expect.objectContaining({ Accept: "application/json" }) }),
    );
  });

  it("maps payload fields to VideoStreamReferenceIO correctly", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_VIDEO_REF]));

    const { useFetchVideoStreamReferences } = await import(
      "~/composables/context/useFetchVideoStreamReferences"
    );
    const { references } = useFetchVideoStreamReferences("do-app-001");
    await flush();

    expect(references.value).toHaveLength(1);
    const ref = references.value[0]!;
    expect(ref.appId).toBe("vid-ref-001");
    expect(ref.name).toBe("My Video");
    expect(ref.mimeType).toBe("video/mp4");
    expect(ref.fileSizeBytes).toBe(1048576);
    expect(ref.durationSeconds).toBe(30.5);
    expect(ref.width).toBe(1920);
    expect(ref.height).toBe(1080);
    expect(ref.frameRate).toBe(29.97);
    expect(ref.videoCodec).toBe("h264");
    expect(ref.audioCodec).toBe("aac");
    expect(ref.wallClockTimestamp).toBe("2023-11-14T22:13:20Z");
  });

  it("leaves references empty and clears isLoading on API error", async () => {
    mockFetch.mockReturnValue(errorResponse(500));

    const { useFetchVideoStreamReferences } = await import(
      "~/composables/context/useFetchVideoStreamReferences"
    );
    const { references, isLoading } = useFetchVideoStreamReferences("do-app-001");
    await flush();

    expect(references.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });

  it("BUG-DO-DETAIL-A-TOAST-2026-06-29 — unwraps the paged envelope { items: [...] }", async () => {
    // Backend has flipped /v2/references list endpoints to a PagedResponseIO
    // envelope. Without unwrap, raw.map(...) throws TypeError and surfaces as
    // the "Error while fetching reference … .map is not a function" toast on
    // the DataObject detail page.
    mockFetch.mockReturnValue(okResponse({ items: [RAW_VIDEO_REF], totalElements: 1 }));

    const { useFetchVideoStreamReferences } = await import(
      "~/composables/context/useFetchVideoStreamReferences"
    );
    const { references, fetchError } = useFetchVideoStreamReferences("do-app-001");
    await flush();

    expect(fetchError.value).toBeNull();
    expect(references.value).toHaveLength(1);
    expect(references.value[0]!.appId).toBe("vid-ref-001");
  });

  it("downloadUrl uses the unified /v2/references/{appId}/content path", async () => {
    mockFetch.mockReturnValue(okResponse([]));

    const { useFetchVideoStreamReferences } = await import(
      "~/composables/context/useFetchVideoStreamReferences"
    );
    const { downloadUrl } = useFetchVideoStreamReferences("do-app-001");
    await flush();

    const url = downloadUrl("vid-ref-001");
    expect(url).toContain("/v2/references/vid-ref-001/content");
    expect(url).not.toContain("/video-stream-references");
  });
});
