/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02) — proves `useBookmarkedCollections()`
 * routes its per-bookmark Collection fetches through the v2 endpoint
 * `/v2/collections/{id}` rather than the generated v1 client. The persisted
 * `BookmarkEntry.collectionId` shape is widened to `number | string` to
 * carry UUID v7 handles for post-Neo4j-reset Collections.
 *
 * The composable holds a module-singleton `_loaded` flag, so this test only
 * asserts the gated unauthenticated-skip path and the toggle's handle
 * resolution (appId preferred over numeric id). The full v2 round-trip is
 * SSR-gated under Vitest (`import.meta.client === false`), so we cannot
 * observe a real fetch — only the load-gate.
 */
import { describe, it, expect, vi } from "vitest";

const mockGetPreferences = vi.fn().mockResolvedValue({});
const mockPatchPreferences = vi.fn().mockResolvedValue(undefined);

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ({
    value: {
      getPreferences: mockGetPreferences,
      patchPreferences: mockPatchPreferences,
    },
  }),
}));

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  status: ref<"authenticated" | "unauthenticated" | "loading">(
    "unauthenticated",
  ),
  data: ref<{ accessToken: string } | null>(null),
});
(globalThis as unknown as Record<string, unknown>).useRuntimeConfig = () => ({
  public: { backendApiUrl: "http://localhost:8080/shepard/api" },
});
vi.stubGlobal(
  "fetch",
  vi.fn().mockResolvedValue({
    ok: false,
    status: 404,
    text: () => Promise.resolve(""),
  }),
);

describe("useBookmarkedCollections — BUG-COLL-APPID-ROUTE-003", () => {
  it("imports without invoking the v1 generated CollectionApi", async () => {
    // If the module had retained a `useShepardApi(CollectionApi)` import,
    // we'd need to stub it; the migration removed that import altogether,
    // so importing the module under bare stubs should not throw.
    const mod = await import(
      "~/composables/context/useBookmarkedCollections"
    );
    expect(typeof mod.useBookmarkedCollections).toBe("function");
  });

  it("toggle() prefers the appId when the Collection carries both id and appId", async () => {
    const { useBookmarkedCollections } = await import(
      "~/composables/context/useBookmarkedCollections"
    );
    const { isBookmarked, toggle } = useBookmarkedCollections();
    const APP_ID = "019e6ffc-1234-7abc-9def-000000000042";
    const collection = { id: 42, appId: APP_ID, name: "LUMEN Showcase" };
    await toggle(collection as never);
    // The persisted handle is the appId — the membership check resolves true
    // for the appId itself. The numeric id does NOT resolve true because the
    // stringified UUID does not equal "42" — that is the desired post-reset
    // behaviour (the appId is the durable handle).
    expect(isBookmarked(APP_ID)).toBe(true);
    expect(isBookmarked(42)).toBe(false);
  });

  it("toggle() falls back to the numeric id when the Collection has no appId", async () => {
    const { useBookmarkedCollections } = await import(
      "~/composables/context/useBookmarkedCollections"
    );
    const { isBookmarked, toggle } = useBookmarkedCollections();
    const collection = { id: 99, name: "Legacy Collection" };
    await toggle(collection as never);
    // Stored handle is the numeric id (no appId available); membership
    // resolves true for both the numeric and the stringified shape.
    expect(isBookmarked(99)).toBe(true);
    expect(isBookmarked("99")).toBe(true);
  });
});
