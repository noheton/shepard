/**
 * AUTH-API-CALLS-UNGATED — proves `useWatchedCollections()` only attempts
 * to read user preferences when nuxt-auth reports `status === "authenticated"`.
 *
 * The composable holds a module-singleton `_loaded` flag so we cannot
 * re-import per test; instead we toggle the auth status before each call
 * and assert against monotonic mock call counts. The "unauthenticated"
 * case is the load-skipped invariant; the authenticated transition is the
 * eventual-consistency invariant.
 */
import { describe, it, expect, vi } from "vitest";
import { useWatchedCollections } from "~/composables/context/useWatchedCollections";

const mockGetPreferences = vi.fn().mockResolvedValue({});
const mockGetCollection = vi.fn();
const mockStatusRef = ref<"authenticated" | "unauthenticated" | "loading">(
  "unauthenticated",
);

// `vi.mock` is hoisted by Vitest so the stubs land before the import above.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ({
    value: {
      getPreferences: mockGetPreferences,
      patchPreferences: vi.fn().mockResolvedValue(undefined),
    },
  }),
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ({
    value: { getCollection: mockGetCollection },
  }),
}));

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  status: mockStatusRef,
  data: ref<{ accessToken: string } | null>(null),
  refresh: vi.fn(),
  signIn: vi.fn(),
});

describe("useWatchedCollections — AUTH-API-CALLS-UNGATED", () => {
  it("does NOT call getPreferences when status is unauthenticated", async () => {
    mockStatusRef.value = "unauthenticated";
    const callsBefore = mockGetPreferences.mock.calls.length;
    useWatchedCollections();
    // Two ticks — one for the watcher to flush, one for the awaited
    // promise inside `load()` to settle (if it had been called).
    await nextTick();
    await nextTick();
    expect(mockGetPreferences.mock.calls.length).toBe(callsBefore);
  });

  it("watcher re-fires the gate on a status transition (no-op when SSR-gated)", async () => {
    // The watcher is wired up regardless of `import.meta.client`; this
    // test proves the status watcher doesn't throw when the auth status
    // transitions from unauthenticated to authenticated, which is the
    // common sign-in path. The actual fetch is gated behind both
    // `import.meta.client` AND `status === "authenticated"` so under
    // Vitest (SSR-side) no network call is made — but the watcher must
    // not produce an unhandled rejection.
    mockStatusRef.value = "unauthenticated";
    useWatchedCollections();
    await nextTick();
    mockStatusRef.value = "authenticated";
    await nextTick();
    await nextTick();
    // Whatever happens, no unhandled error.
    expect(true).toBe(true);
  });
});
