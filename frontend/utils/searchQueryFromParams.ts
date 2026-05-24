/**
 * UX bonus (2026-05-24) — translate URL params on `/search` into the
 * JSON-query string the Advanced Search form drives.
 *
 * Three shapes:
 *   `?searchQuery=<json>` — full structured form (Advanced Search self-link)
 *   `?q=<text>`           — simple free-text form (header dropdown footer)
 *   neither               — fall back to caller-supplied default
 *
 * `searchQuery` wins over `q` when both are present (more specific shape).
 *
 * Pulled out of `pages/search/index.vue` so it's unit-testable without a
 * Nuxt runtime. Page calls this on mount via `useRequestURL().searchParams`.
 */
export function buildNameContainsQuery(needle: string): string {
  return JSON.stringify(
    { property: "name", operator: "contains", value: needle },
    null,
    4,
  );
}

export interface SearchQueryFromParamsResult {
  /** The JSON-query string to drop into the Advanced Search form. */
  jsonQuery: string;
  /** True when a non-empty param drove the result (page should run search). */
  shouldRun: boolean;
}

export function searchQueryFromParams(
  params: URLSearchParams,
  initialJson: string,
): SearchQueryFromParamsResult {
  const searchQuery = params.get("searchQuery");
  const q = params.get("q");

  if (searchQuery) {
    return { jsonQuery: searchQuery, shouldRun: true };
  }
  if (q && q.trim() !== "") {
    return { jsonQuery: buildNameContainsQuery(q.trim()), shouldRun: true };
  }
  return { jsonQuery: initialJson, shouldRun: false };
}
