/**
 * UU2 — UI-STALE-URL-HINT (2026-05-31).
 *
 * Helpers for deciding whether a route-param `id` is a current-shape Shepard
 * `appId` (UUID v7) or a pre-Neo4j-wipe legacy numeric Neo4j long. Used by
 * the `EntityNotFound.vue` empty state to surface a "this URL uses a numeric
 * id" hint when the user lands on a bookmarked URL from before the last data
 * reset (e.g. `https://shepard.nuclide.systems/collections/1787/dataobjects/
 * 1792` — the canonical operator repro).
 *
 * The UUID regex is intentionally loose (doesn't enforce v7-specific bits) —
 * the goal is "does this look like the appId shape", not cryptographic
 * validation. Matches the shape used by `isPlausibleAppId` in
 * `utils/toolsLanding.ts` (which now re-exports from here per the
 * `reuse-before-reimplement` guard).
 */

const UUID_LOOSE_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ALL_DIGITS_RE = /^\d+$/;

/** True iff `input` looks like a (UUID v7-shaped) Shepard appId. */
export function isPlausibleAppId(input: string | null | undefined): boolean {
  if (!input) return false;
  return UUID_LOOSE_RE.test(input.trim().toLowerCase());
}

/** True iff `input` is a non-empty all-digits string (legacy Neo4j long). */
export function isNumericLegacyId(input: string | null | undefined): boolean {
  if (!input) return false;
  return ALL_DIGITS_RE.test(input.trim());
}
