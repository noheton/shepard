/**
 * UX612-C1 — unit tests for `useFetchReferenceV2`.
 *
 * The TimeseriesReference detail page's route param is the v2 appId
 * (frontend-v2-only rule). Pre-fix the page resolved it through
 * `resolveNumericId(undefined, <uuid>)` → undefined → the v1 fetch never
 * fired → eternal spinner. The fix loads the reference via
 * `GET /v2/references/{appId}` and resolves the numeric id the
 * still-v1-only sub-calls need from the loaded v2 entity's `.id`.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useFetchReferenceV2 } from "~/composables/context/useFetchReferenceV2";

const { getReference } = vi.hoisted(() => ({ getReference: vi.fn() }));

vi.mock("@dlr-shepard/backend-client", () => ({
  ReferencesApi: function ReferencesApi() {},
}));

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ({ value: { getReference } }),
}));

const APP_ID = "019e6ffc-aaaa-7bcd-9eef-000000000042";

/** Flush microtask queue so the watch-triggered async fetch completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useFetchReferenceV2 — appId route param (UX612-C1)", () => {
  it("loads the reference by the appId route param via GET /v2/references/{appId}", async () => {
    getReference.mockResolvedValue({
      id: 364329,
      appId: APP_ID,
      name: "tr-004-sensors",
      kind: "timeseries",
      payload: { timeseriesContainerAppId: "019e6ffc-bbbb-7bcd-9eef-000000000001" },
    });

    const { referenceV2 } = useFetchReferenceV2(() => APP_ID);
    await flush();

    // The appId is passed through verbatim — no numeric coercion of the UUID.
    expect(getReference).toHaveBeenCalledWith({ appId: APP_ID });
    expect(referenceV2.value?.appId).toBe(APP_ID);
  });

  it("exposes the loaded v2 entity's numeric `.id` for still-v1-only sub-calls", async () => {
    getReference.mockResolvedValue({ id: 364329, appId: APP_ID, name: "r" });

    const { referenceV2 } = useFetchReferenceV2(() => APP_ID);
    await flush();

    // The page computes timeseriesReferenceNumericId from this — never from
    // the route param (the deleted numeric-fallback branch).
    expect(referenceV2.value?.id).toBe(364329);
  });

  it("does not fetch while the appId is undefined", async () => {
    const appId = ref<string | undefined>(undefined);
    useFetchReferenceV2(() => appId.value);
    await flush();
    expect(getReference).not.toHaveBeenCalled();
  });

  it("fetches once the appId becomes available (deferred route hydration)", async () => {
    getReference.mockResolvedValue({ id: 1, appId: APP_ID, name: "r" });
    const appId = ref<string | undefined>(undefined);
    const { referenceV2 } = useFetchReferenceV2(() => appId.value);
    await flush();
    expect(getReference).not.toHaveBeenCalled();

    appId.value = APP_ID;
    await flush();
    expect(getReference).toHaveBeenCalledWith({ appId: APP_ID });
    expect(referenceV2.value?.id).toBe(1);
  });

  it("flags notFound on a 404 instead of spinning forever", async () => {
    getReference.mockRejectedValue({ response: { status: 404 } });

    const { referenceV2, notFound } = useFetchReferenceV2(() => APP_ID);
    await flush();

    expect(notFound.value).toBe(true);
    expect(referenceV2.value).toBeUndefined();
  });

  it("leaves notFound false and routes other errors to handleError", async () => {
    getReference.mockRejectedValue({ response: { status: 500 } });

    const { notFound } = useFetchReferenceV2(() => APP_ID);
    await flush();

    expect(notFound.value).toBe(false);
    expect(
      (globalThis as unknown as { handleError: ReturnType<typeof vi.fn> })
        .handleError,
    ).toHaveBeenCalled();
  });

  it("refresh() re-fetches with the current appId", async () => {
    getReference.mockResolvedValue({ id: 2, appId: APP_ID, name: "r" });
    const { refresh } = useFetchReferenceV2(() => APP_ID);
    await flush();
    expect(getReference).toHaveBeenCalledTimes(1);

    refresh();
    await flush();
    expect(getReference).toHaveBeenCalledTimes(2);
  });
});
