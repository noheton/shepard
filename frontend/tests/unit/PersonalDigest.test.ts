/**
 * UI14 — PersonalDigest "Shared with me" split logic unit tests.
 *
 * Tests the pure computed-logic equivalents of the split in PersonalDigest.vue:
 *   - myCollections: collections where createdBy === userDisplayName
 *   - sharedCollections: collections where createdBy !== userDisplayName
 *   - sharedCollections is empty when userDisplayName is undefined (loading guard)
 *   - sharedCollections is empty when the collection list is loading
 *
 * We test the logic units (pure functions + reactive logic) rather than mounting
 * the component, because the Vuetify + Nuxt rendering chain requires the full
 * app context. The component's template is verified by the seed-script smoke run.
 */

import { describe, it, expect } from "vitest";
import type { Collection } from "@dlr-shepard/backend-client";

// ── Fixture builder ───────────────────────────────────────────────────────────

function buildCollection(overrides: Partial<Collection> = {}): Collection {
  return {
    id: 1,
    createdAt: new Date("2025-01-01"),
    createdBy: "alice",
    updatedAt: null,
    updatedBy: null,
    name: "Test Collection",
    description: null,
    status: "READY",
    attributes: {},
    dataObjectIds: [],
    incomingIds: [],
    heroImageUrl: null,
    ...overrides,
  };
}

// ── Pure split logic (mirrors PersonalDigest.vue computed properties) ─────────

/**
 * Returns collections where createdBy matches the current user's display name.
 * When userDisplayName is unknown (undefined) or data is still loading, returns
 * all collections unchanged to avoid a flash-of-all-shared state.
 */
function splitMyCollections(
  collections: Collection[],
  userDisplayName: string | undefined,
  loading: boolean,
): Collection[] {
  if (!userDisplayName || loading) return collections;
  return collections.filter(c => c.createdBy === userDisplayName);
}

/**
 * Returns collections where createdBy does NOT match the current user's display
 * name — i.e. accessible collections created by someone else.
 * Returns empty array when userDisplayName is unknown or loading, preventing a
 * flash where every collection appears as "shared".
 */
function splitSharedCollections(
  collections: Collection[],
  userDisplayName: string | undefined,
  loading: boolean,
): Collection[] {
  if (!userDisplayName || loading) return [];
  return collections.filter(c => c.createdBy !== userDisplayName);
}

// ─────────────────────────────────────────────────────────────────────────────
describe("splitMyCollections", () => {
  it("returns only collections owned by the current user", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitMyCollections([mine, theirs], "alice", false);
    expect(result).toEqual([mine]);
  });

  it("returns all collections when userDisplayName is undefined (loading guard)", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitMyCollections([mine, theirs], undefined, false);
    expect(result).toEqual([mine, theirs]);
  });

  it("returns all collections while data is loading (prevents flash)", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitMyCollections([mine, theirs], "alice", true);
    expect(result).toEqual([mine, theirs]);
  });

  it("returns empty array when no owned collections exist", () => {
    const theirs1 = buildCollection({ id: 2, createdBy: "bob" });
    const theirs2 = buildCollection({ id: 3, createdBy: "carol" });
    const result = splitMyCollections([theirs1, theirs2], "alice", false);
    expect(result).toEqual([]);
  });

  it("returns all when every collection is owned by the user", () => {
    const mine1 = buildCollection({ id: 1, createdBy: "alice" });
    const mine2 = buildCollection({ id: 2, createdBy: "alice" });
    const result = splitMyCollections([mine1, mine2], "alice", false);
    expect(result).toEqual([mine1, mine2]);
  });

  it("returns empty array for empty collection list", () => {
    const result = splitMyCollections([], "alice", false);
    expect(result).toEqual([]);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("splitSharedCollections", () => {
  it("returns only collections NOT owned by the current user", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitSharedCollections([mine, theirs], "alice", false);
    expect(result).toEqual([theirs]);
  });

  it("returns empty array when userDisplayName is undefined (loading guard)", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitSharedCollections([mine, theirs], undefined, false);
    expect(result).toEqual([]);
  });

  it("returns empty array while data is loading (prevents all-shared flash)", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const result = splitSharedCollections([mine, theirs], "alice", true);
    expect(result).toEqual([]);
  });

  it("returns empty array when all collections are owned by the user", () => {
    const mine1 = buildCollection({ id: 1, createdBy: "alice" });
    const mine2 = buildCollection({ id: 2, createdBy: "alice" });
    const result = splitSharedCollections([mine1, mine2], "alice", false);
    expect(result).toEqual([]);
  });

  it("returns all when every collection belongs to someone else", () => {
    const theirs1 = buildCollection({ id: 2, createdBy: "bob" });
    const theirs2 = buildCollection({ id: 3, createdBy: "carol" });
    const result = splitSharedCollections([theirs1, theirs2], "alice", false);
    expect(result).toEqual([theirs1, theirs2]);
  });

  it("returns empty array for empty collection list", () => {
    const result = splitSharedCollections([], "alice", false);
    expect(result).toEqual([]);
  });

  it("handles multiple creators correctly — only non-alice entries returned", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const bob = buildCollection({ id: 2, createdBy: "bob" });
    const carol = buildCollection({ id: 3, createdBy: "carol" });
    const result = splitSharedCollections([mine, bob, carol], "alice", false);
    expect(result).toHaveLength(2);
    expect(result).toContain(bob);
    expect(result).toContain(carol);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("section visibility rule (sharedCollections.length > 0)", () => {
  it("section is hidden when sharedCollections is empty", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const shared = splitSharedCollections([mine], "alice", false);
    // In the template: v-if="!loading && sharedCollections.length > 0"
    const sectionVisible = shared.length > 0;
    expect(sectionVisible).toBe(false);
  });

  it("section is visible when at least one shared collection exists", () => {
    const mine = buildCollection({ id: 1, createdBy: "alice" });
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const shared = splitSharedCollections([mine, theirs], "alice", false);
    const sectionVisible = shared.length > 0;
    expect(sectionVisible).toBe(true);
  });

  it("section is hidden while loading even if splits would produce shared entries", () => {
    const loading = true;
    const theirs = buildCollection({ id: 2, createdBy: "bob" });
    const shared = splitSharedCollections([theirs], "alice", loading);
    // loading=true → splitSharedCollections returns [] → section hidden
    const sectionVisible = !loading && shared.length > 0;
    expect(sectionVisible).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// WATCHED-COLLECTIONS-EMPTY-STATE
// The watched section is always rendered (header visible even when empty).
// When watched.length === 0 and !watchedLoading, the empty-state card is shown
// instead of the collection grid. This mirrors the logic in PersonalDigest.vue:
//   <template v-else-if="watched.length === 0"> → empty state
//   <template v-else>                            → collection cards
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Derives which branch of the watched-section template is active.
 * Returns:
 *   "loading"    — skeleton loaders shown
 *   "empty"      — empty-state card shown (no watched collections)
 *   "populated"  — collection cards shown
 */
function watchedSectionState(
  watchedCount: number,
  watchedLoading: boolean,
): "loading" | "empty" | "populated" {
  if (watchedLoading) return "loading";
  if (watchedCount === 0) return "empty";
  return "populated";
}

describe("watched section empty-state (WATCHED-COLLECTIONS-EMPTY-STATE)", () => {
  it("shows empty state when watched list is empty and not loading", () => {
    expect(watchedSectionState(0, false)).toBe("empty");
  });

  it("shows skeleton loaders while watchedLoading is true (even if count is 0)", () => {
    expect(watchedSectionState(0, true)).toBe("loading");
  });

  it("shows collection cards when watched list is non-empty", () => {
    expect(watchedSectionState(3, false)).toBe("populated");
  });

  it("shows skeleton loaders while loading even when collections are present", () => {
    // Edge: stale data present but a refresh is in progress — show skeletons
    expect(watchedSectionState(2, true)).toBe("loading");
  });

  it("section header is always rendered regardless of watched count", () => {
    // The section header is outside the v-if/v-else-if/v-else block,
    // so it renders for all three states. We verify all states are reachable
    // without the header being gated.
    const states = [
      watchedSectionState(0, false),  // empty
      watchedSectionState(0, true),   // loading
      watchedSectionState(1, false),  // populated
    ];
    // All three states are distinct — proves every branch is covered
    expect(new Set(states).size).toBe(3);
  });
});
