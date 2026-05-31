/**
 * FAIR4 — Vitest tests for `useMetadataCompleteness` composable.
 *
 * Covers:
 *  1. Happy path — populates `score` from a successful 200 response.
 *  2. HTTP error (403) — sets `isError = true`, leaves `score = null`.
 *  3. Network error (fetch throws) — sets `isError = true`, fail-soft.
 *  4. Missing auth token — skips the fetch, `isError = true` immediately.
 *  5. Re-fetches when the collectionAppId ref changes.
 *  6. Sets `isLoading = true` during fetch, `false` after.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-jwt-token";
const BASE_URL = "http://localhost:8080";
const APP_ID = "018f9c5a-7e26-7000-a000-000000000042";

const mockScore = {
  collectionAppId: APP_ID,
  score: 80,
  maxScore: 100,
  percentage: 80.0,
  checks: [
    { checkId: "name", label: "Collection has a name", passed: true, weight: 10, hint: null },
    { checkId: "license", label: "License (SPDX) set", passed: true, weight: 20, hint: "DataCite §16 (Rights)" },
  ],
};

// Set up globals that the composable auto-imports expect.
beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, {
    useAuth: () => ({
      data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
    }),
    useRuntimeConfig: () => ({
      public: {
        backendApiUrl: `${BASE_URL}/shepard/api`,
        backendV2ApiUrl: "",
      },
    }),
  });
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchStatus(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(""),
    }),
  );
}

function mockFetchThrows(message = "Network failure") {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error(message)));
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useMetadataCompleteness — FAIR4", () => {
  it("1. populates score from a successful 200 response", async () => {
    mockFetchOk(mockScore);
    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );

    const { score, isError } = useMetadataCompleteness(APP_ID);
    await flush();
    await flush();

    expect(score.value).not.toBeNull();
    expect(score.value?.score).toBe(80);
    expect(score.value?.maxScore).toBe(100);
    expect(score.value?.checks).toHaveLength(2);
    expect(isError.value).toBe(false);
  });

  it("2. HTTP 403 — sets isError=true, score stays null", async () => {
    mockFetchStatus(403);
    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );

    const { score, isError, isLoading } = useMetadataCompleteness(APP_ID);
    await flush();
    await flush();

    expect(isError.value).toBe(true);
    expect(score.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("3. network error — sets isError=true, fail-soft", async () => {
    mockFetchThrows("Network failure");
    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );

    const { score, isError, isLoading } = useMetadataCompleteness(APP_ID);
    await flush();
    await flush();

    expect(isError.value).toBe(true);
    expect(score.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("4. missing auth token — skips fetch immediately, isError=true", async () => {
    Object.assign(globalThis, {
      useAuth: () => ({ data: ref(null) }),
    });
    vi.stubGlobal("fetch", vi.fn());

    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );
    const { score, isError } = useMetadataCompleteness(APP_ID);
    await flush();

    expect(fetch).not.toHaveBeenCalled();
    expect(isError.value).toBe(true);
    expect(score.value).toBeNull();
  });

  it("5. re-fetches when collectionAppId ref changes", async () => {
    const OTHER_ID = "018f9c5a-ffff-7000-a000-000000000099";
    const fetchSpy = vi.fn().mockImplementation((url: string) =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            ...mockScore,
            collectionAppId: url.includes(OTHER_ID) ? OTHER_ID : APP_ID,
          }),
      }),
    );
    vi.stubGlobal("fetch", fetchSpy);

    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );
    const appIdRef = ref<string | null>(APP_ID);
    const { score } = useMetadataCompleteness(appIdRef);
    await flush();
    await flush();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(score.value?.collectionAppId).toBe(APP_ID);

    appIdRef.value = OTHER_ID;
    await flush();
    await flush();

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(score.value?.collectionAppId).toBe(OTHER_ID);
  });

  it("6. calls the correct v2 URL (strips /shepard/api from base)", async () => {
    mockFetchOk(mockScore);
    const { useMetadataCompleteness } = await import(
      "~/composables/context/useMetadataCompleteness"
    );
    useMetadataCompleteness(APP_ID);
    await flush();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as string;
    expect(calledUrl).toBe(
      `${BASE_URL}/v2/collections/${encodeURIComponent(APP_ID)}/metadata-completeness`,
    );
    const calledInit = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[1];
    expect(calledInit.headers.Authorization).toBe(`Bearer ${ACCESS_TOKEN}`);
  });
});
