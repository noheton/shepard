/**
 * Tolerantly coerce a JSON response body into a list.
 *
 * The fork's paged endpoints return an envelope
 * (`{ items, total, page, pageSize }`) after the APISIMP pagination sweep;
 * older/unpaged endpoints return a bare array. Hand-rolled
 * `fetch(...).json() as T[]` consumers that assume a bare array crash when an
 * endpoint is (or becomes) enveloped — the value is then a non-array object,
 * so `.length` is `undefined` and `for`/`v-for` iterate the object's values
 * (see WATCH-ENVELOPE-UNWRAP, which crashed the entire Add-watch panel).
 *
 * This helper handles both shapes (and any non-conforming value → `[]`), so it
 * is safe to apply uniformly: it fixes enveloped endpoints and is a no-op on
 * bare-array ones. The generated `@dlr-shepard/backend-client` already unwraps
 * envelopes — this is only for the hand-rolled `fetch` composables.
 */
export function unwrapList<T = unknown>(json: unknown): T[] {
  if (Array.isArray(json)) return json as T[];
  const items = (json as { items?: unknown } | null)?.items;
  return (Array.isArray(items) ? items : []) as T[];
}
