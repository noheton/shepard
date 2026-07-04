/**
 * DMP-DOWNLOAD-NAV-01 — pure helpers for the DownloadDmpButton.
 *
 * Extracted so unit tests can import without mounting Vue / Vuetify.
 * Mirrors the inline-helper pattern of EditFileReferenceDialog and
 * the inline-helper pattern used across the file-reference components.
 *
 * The DMP-snippet endpoint (FAIR7) is `GET
 * /v2/collections/{appId}/dmp-snippet` and supports content negotiation
 * (`text/markdown` default, `application/json` wrapper) — see
 * `backend/src/main/java/de/dlr/shepard/v2/fair/resources/DmpSnippetV2Rest.java`.
 * This button always pulls the Markdown variant for the download
 * because that's the copy-paste-ready format DFG / EU Horizon Europe
 * researchers actually paste into their DMP forms.
 */

/**
 * Build the FAIR7 endpoint URL for a given Collection appId.
 *
 * @param baseUrl  the `/v2`-prefix base (e.g. `https://shepard-api.…`),
 *                 already stripped of any trailing slash.
 * @param appId    the Collection appId (UUID v7).
 */
export function dmpSnippetUrl(baseUrl: string, appId: string): string {
  return `${baseUrl}/v2/collections/${encodeURIComponent(appId)}/dmp-snippet`;
}

/**
 * Build the suggested download filename. Collection names get
 * sanitised to be filesystem-safe; we strip everything that's not
 * `[A-Za-z0-9._-]` and collapse runs of `-`. Empty / whitespace-only
 * names fall back to the appId-prefixed default.
 */
export function dmpFilenameFor(
  collectionName: string | undefined | null,
  appId: string,
): string {
  const safe = (collectionName ?? "")
    .normalize("NFKD")
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .trim();
  const stem = safe.length > 0 ? safe : `collection-${appId.slice(0, 8)}`;
  return `${stem}-dmp-snippet.md`;
}
