/**
 * TOOLS-CONTEXT-VOCAB-BACKEND-1 — URL builder for the vocab browser page.
 *
 * Pulled out as a pure helper so the URL-construction logic — including
 * the "filtered vs. unfiltered" branch — can be unit-tested without
 * mounting the Nuxt page (mirrors the toolsContext.ts shape).
 */

export type VocabBrowserScope = "collection" | "data-object";

/**
 * Build the v2 endpoint URL for the vocabulary browser, branching on
 * whether a `usedBy` filter is active.
 *
 *  - No filter → `GET /v2/semantic/vocabularies` (full inventory).
 *  - Filter active → `GET /v2/semantic/vocabularies/used-by/{appId}?scope=…`
 *    (TOOLS-CONTEXT-VOCAB-BACKEND-1 endpoint shipped 2026-05-30).
 *
 * The appId is URI-component-encoded so non-UUID-shaped values
 * (legacy / malformed inputs) don't produce malformed URLs.
 * The scope is normalised to `"data-object"` for anything other than the
 * literal string `"collection"` — matching backend's default.
 */
export function buildVocabBrowserUrl(
  v2Base: string,
  usedByAppId: string | null,
  scope: string | null,
): string {
  const base = v2Base.replace(/\/$/, "");
  if (!usedByAppId) {
    return `${base}/v2/semantic/vocabularies`;
  }
  const safeScope: VocabBrowserScope =
    scope === "collection" ? "collection" : "data-object";
  return (
    `${base}/v2/semantic/vocabularies/used-by/${encodeURIComponent(usedByAppId)}` +
    `?scope=${encodeURIComponent(safeScope)}`
  );
}
