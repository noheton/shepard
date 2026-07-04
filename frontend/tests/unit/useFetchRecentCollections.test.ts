/**
 * Unit coverage for `useFetchRecentCollections()`.
 *
 * The auto-triggered `fetch()` is SSR-gated under Vitest
 * (`import.meta.client === false` in the `node` environment), so — exactly
 * like `useBookmarkedCollections.test.ts` — we cannot observe a real API
 * round-trip here. What we CAN assert without the network path is:
 *   - the exported pure helpers (`isClosedCollection` / `isCleanupCollection`),
 *   - the initial reactive state (loading=true, empty collections, no error),
 *   - the `showClosed` toggle + the `filteredCollections` / `hasClosedCollections`
 *     computeds, driven by seeding `allCollections` directly.
 *
 * The networked happy/refetch/error paths are exercised at the integration
 * layer (deployed UI + Playwright), not in this SSR-gated unit slice.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  useFetchRecentCollections,
  isClosedCollection,
  isCleanupCollection,
} from "~/composables/context/useFetchRecentCollections";
import type { Collection } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

const mockGetAllCollections = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getAllCollections: mockGetAllCollections }),
  );
});

const coll = (id: number, status?: string): Collection =>
  ({ id, name: `C${id}`, status }) as unknown as Collection;

describe("useFetchRecentCollections — pure helpers", () => {
  it("isClosedCollection is true only for CLOSED status (case-insensitive)", () => {
    expect(isClosedCollection(coll(1, "CLOSED"))).toBe(true);
    expect(isClosedCollection(coll(2, "closed"))).toBe(true);
    expect(isClosedCollection(coll(3, "OPEN"))).toBe(false);
    expect(isClosedCollection(coll(4))).toBe(false);
  });

  it("isCleanupCollection is true only for PENDING_CLEANUP status", () => {
    expect(isCleanupCollection(coll(1, "PENDING_CLEANUP"))).toBe(true);
    expect(isCleanupCollection(coll(2, "pending_cleanup"))).toBe(true);
    expect(isCleanupCollection(coll(3, "CLOSED"))).toBe(false);
  });
});

describe("useFetchRecentCollections — reactive surface", () => {
  it("starts with loading=true, empty collections, and no error", () => {
    const { loading, collections, allCollections, error, showClosed } =
      useFetchRecentCollections();
    expect(loading.value).toBe(true);
    expect(collections.value).toEqual([]);
    expect(allCollections.value).toEqual([]);
    expect(error.value).toBeNull();
    expect(showClosed.value).toBe(false);
  });

  it("filteredCollections hides CLOSED entries until showClosed is toggled", () => {
    const { collections, allCollections, showClosed, hasClosedCollections } =
      useFetchRecentCollections();

    // Seed allCollections directly — the SSR-gated fetch never runs in Vitest.
    allCollections.value = [coll(1, "OPEN"), coll(2, "CLOSED")];

    // Default view excludes CLOSED.
    expect(collections.value.map(c => c.id)).toEqual([1]);
    expect(hasClosedCollections.value).toBe(true);

    // Toggling showClosed surfaces every collection.
    showClosed.value = true;
    expect(collections.value.map(c => c.id)).toEqual([1, 2]);
  });

  it("hasClosedCollections is false when no CLOSED collection is present", () => {
    const { allCollections, hasClosedCollections } =
      useFetchRecentCollections();
    allCollections.value = [coll(1, "OPEN"), coll(2, "READY")];
    expect(hasClosedCollections.value).toBe(false);
  });

  it("exposes a refetch handle", () => {
    const { refetch } = useFetchRecentCollections();
    expect(typeof refetch).toBe("function");
  });
});
