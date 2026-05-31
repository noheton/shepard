/**
 * AUTH-API-CALLS-UNGATED — proves `useFetchUserProfile()` only calls the
 * backend when nuxt-auth reports `status === "authenticated"`.
 *
 * Pre-fix the composable fired on every mount regardless of auth state;
 * the resulting 401 was absorbed by `useAuthRefreshMiddleware`'s suppression
 * guard (BUG-SIGNOUT-LOOP-1). The gate added 2026-05-31 is the cleaner shape.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";

const mockGetCurrentUser = vi.fn().mockResolvedValue({ username: "alice" });
const mockStatusRef = ref<"authenticated" | "unauthenticated" | "loading">(
  "unauthenticated",
);

// Stub the API factory: `useShepardApi(UserApi).value.getCurrentUser()` —
// `vi.mock` is hoisted above imports by Vitest, so this binds correctly.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ({
    value: { getCurrentUser: mockGetCurrentUser },
  }),
}));

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  status: mockStatusRef,
  data: ref<{ accessToken: string } | null>(null),
  refresh: vi.fn(),
  signIn: vi.fn(),
});

beforeEach(() => {
  vi.clearAllMocks();
  mockStatusRef.value = "unauthenticated";
});

describe("useFetchUserProfile — AUTH-API-CALLS-UNGATED", () => {
  it("does NOT fetch when status is unauthenticated", async () => {
    mockStatusRef.value = "unauthenticated";
    const callsBefore = mockGetCurrentUser.mock.calls.length;
    const { user } = useFetchUserProfile();
    await nextTick();
    expect(mockGetCurrentUser.mock.calls.length).toBe(callsBefore);
    expect(user.value).toBeUndefined();
  });

  it("does NOT fetch when status is loading", async () => {
    mockStatusRef.value = "loading";
    const callsBefore = mockGetCurrentUser.mock.calls.length;
    useFetchUserProfile();
    await nextTick();
    expect(mockGetCurrentUser.mock.calls.length).toBe(callsBefore);
  });

  it("fetches when status is authenticated at mount", async () => {
    mockStatusRef.value = "authenticated";
    const callsBefore = mockGetCurrentUser.mock.calls.length;
    useFetchUserProfile();
    await nextTick();
    expect(mockGetCurrentUser.mock.calls.length).toBeGreaterThan(callsBefore);
  });

  it("fetches when status transitions to authenticated after mount", async () => {
    mockStatusRef.value = "unauthenticated";
    const callsBefore = mockGetCurrentUser.mock.calls.length;
    useFetchUserProfile();
    await nextTick();
    expect(mockGetCurrentUser.mock.calls.length).toBe(callsBefore);

    mockStatusRef.value = "authenticated";
    await nextTick();
    expect(mockGetCurrentUser.mock.calls.length).toBeGreaterThan(callsBefore);
  });
});
