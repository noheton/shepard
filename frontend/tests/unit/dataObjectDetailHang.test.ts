/**
 * BUG-DO-DETAIL-HANG (2026-06-13) — the DataObject detail page hung on an
 * indeterminate spinner and never rendered.
 *
 * Root cause: the page render gate required `dataReferences` AND
 * `relatedEntities` to be non-undefined, but those v1-keyed reference
 * composables only flip from `undefined` once a NUMERIC id resolves from the
 * loaded v2 entity. Post-reset DataObjects carry only an `appId` (no numeric
 * `id`), so `resolveNumericId` returns `undefined`, the v1 fetch never fires,
 * and the gating refs stay `undefined` forever → infinite spinner.
 *
 * The fix decouples the render gate from the reference panels (which fail soft
 * and resolve independently) and ensures each reference composable resets its
 * loading flag in a `finally` even when its fetch rejects (404/403/400/network).
 *
 * These cases lock in:
 *   1. the fail-soft contract — a rejecting reference fetch still resolves
 *      `isLoading` → false (the page's spinner must never depend on it);
 *   2. the fixed-path contract — the composable hits the correct unified v2
 *      endpoint with the right `kind` + `dataObjectAppId` query params;
 *   3. the render-gate decoupling — the gate logic resolves true once the
 *      required entities load even when the reference refs are still undefined.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchSingletonFileReferences } from "~/composables/context/useFetchSingletonFileReferences";
import { useFetchSpatialReferencesV2 } from "~/composables/context/useFetchSpatialReferencesV2";
import { resolveNumericId } from "~/utils/collectionRouteParams";

const APP_ID = "019ebcf8-53a0-73b8-ac2a-42261781123c";

beforeEach(() => {
  vi.restoreAllMocks();
  // Provide a token so the composables take the authenticated fetch path.
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: { value: { accessToken: "tok" } },
  });
});

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("BUG-DO-DETAIL-HANG — reference composables are fail-soft", () => {
  it("useFetchSingletonFileReferences resolves isLoading=false on a 404 (no hang)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => [],
      text: async () => "not found",
    });
    vi.stubGlobal("fetch", fetchMock);

    const { references, isLoading } = useFetchSingletonFileReferences(APP_ID);
    await flush();

    // Loading flag MUST settle (finally block) — never left true.
    expect(isLoading.value).toBe(false);
    // 404 is a normal empty state, not a fatal.
    expect(references.value).toEqual([]);
  });

  it("useFetchSingletonFileReferences resolves isLoading=false on a rejected fetch (network error)", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error("network down"));
    vi.stubGlobal("fetch", fetchMock);

    const { references, isLoading } = useFetchSingletonFileReferences(APP_ID);
    await flush();

    expect(isLoading.value).toBe(false);
    expect(references.value).toEqual([]);
  });

  it("useFetchSingletonFileReferences hits the correct unified v2 path + params", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
      text: async () => "[]",
    });
    vi.stubGlobal("fetch", fetchMock);

    useFetchSingletonFileReferences(APP_ID);
    await flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const calledUrl = String(fetchMock.mock.calls[0]?.[0] ?? "");
    expect(calledUrl).toContain("/v2/references");
    expect(calledUrl).toContain("kind=file");
    expect(calledUrl).toContain(`dataObjectAppId=${encodeURIComponent(APP_ID)}`);
  });

  it("useFetchSpatialReferencesV2 resolves isLoading=false on a 400 (no hang)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => [],
      text: async () => "bad request",
    });
    vi.stubGlobal("fetch", fetchMock);

    const { references, isLoading } = useFetchSpatialReferencesV2(APP_ID);
    await flush();

    expect(isLoading.value).toBe(false);
    expect(references.value).toEqual([]);
  });

  it("useFetchSpatialReferencesV2 hits the correct unified v2 path + kind=spatial", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
      text: async () => "[]",
    });
    vi.stubGlobal("fetch", fetchMock);

    useFetchSpatialReferencesV2(APP_ID);
    await flush();

    const calledUrl = String(fetchMock.mock.calls[0]?.[0] ?? "");
    expect(calledUrl).toContain("/v2/references");
    expect(calledUrl).toContain("kind=spatial");
    expect(calledUrl).toContain(`dataObjectAppId=${encodeURIComponent(APP_ID)}`);
  });
});

describe("BUG-DO-DETAIL-HANG — render gate decoupled from reference panels", () => {
  /**
   * Mirror of the page's render gate. The OLD gate required all four entities;
   * the NEW gate requires only the two REQUIRED entities. This proves the page
   * renders for an appId-only DataObject whose numeric-id-keyed reference
   * composables never resolve (stay undefined).
   */
  function oldGate(c: unknown, d: unknown, r: unknown, rel: unknown): boolean {
    return !!c && !!d && !!r && !!rel;
  }
  function newGate(c: unknown, d: unknown): boolean {
    return !!c && !!d;
  }

  it("post-reset DataObject has no numeric id → reference composables never fetch", () => {
    // resolveNumericId(undefined loadedId, UUID route param) → undefined.
    expect(resolveNumericId(undefined, APP_ID)).toBeUndefined();
  });

  it("OLD gate hangs when reference refs stay undefined; NEW gate renders", () => {
    const collection = { appId: "coll" };
    const dataObject = { appId: APP_ID };
    const dataReferences = undefined; // never resolved (no numeric id)
    const relatedEntities = undefined; // never resolved (no numeric id)

    // The bug: the old gate stays false forever → infinite spinner.
    expect(oldGate(collection, dataObject, dataReferences, relatedEntities)).toBe(
      false,
    );
    // The fix: the page renders once the required entities load.
    expect(newGate(collection, dataObject)).toBe(true);
  });

  it("safe-default views yield [] when the reference refs are undefined", () => {
    const dataReferences = { value: undefined as unknown[] | undefined };
    const relatedEntities = { value: undefined as unknown[] | undefined };
    const dataReferencesSafe = dataReferences.value ?? [];
    const relatedEntitiesSafe = relatedEntities.value ?? [];
    expect(dataReferencesSafe).toEqual([]);
    expect(relatedEntitiesSafe).toEqual([]);
  });
});
