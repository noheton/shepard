/**
 * Utility for building the canonical JupyterHub launch URL used by the
 * "Open in JupyterHub" action on Notebook rows (J1e-PR-06-AUTOFETCH-02).
 *
 * Shape:
 *   {hubBase}/hub/spawn?file={encodeURIComponent(shepardFileDownloadUrl)}
 *
 * where `shepardFileDownloadUrl` is:
 *   {v2BaseUrl}/v2/files/{encodeURIComponent(appId)}/content
 *
 * The `?file=` value MUST be percent-encoded so that the JupyterHub
 * pre-spawn hook can parse the outer URL without ambiguity — any `?`, `&`,
 * `=` characters in the inner Shepard URL would break URL parsing if left
 * unencoded.  `encodeURIComponent` is the correct encoding: it escapes all
 * characters that are special in a query-parameter value.
 *
 * Round-trip invariant (verified by unit tests):
 *   decodeURIComponent(encodedFileParam) === originalShepardDownloadUrl
 */

/**
 * Build the Shepard file-content download URL for a singleton FileReference.
 *
 * @param v2BaseUrl  The base URL of the Shepard v2 API (no trailing slash),
 *                   e.g. "https://shepard.example.org".
 * @param appId      The FileReference appId (UUID v7).
 * @returns          The absolute download URL for the file content.
 */
export function buildShepardFileDownloadUrl(v2BaseUrl: string, appId: string): string {
  const base = v2BaseUrl.replace(/\/$/, "");
  return `${base}/v2/files/${encodeURIComponent(appId)}/content`;
}

/**
 * Build the canonical JupyterHub launch URL for a singleton FileReference.
 *
 * The `?file=` query-parameter value is percent-encoded with
 * `encodeURIComponent` so the JupyterHub server can safely parse the outer
 * URL even when the inner Shepard download URL contains `?`, `&`, `=`, or
 * other query-string special characters.
 *
 * @param hubUrl     The JupyterHub base URL (may have trailing slash),
 *                   e.g. "https://shepard.example.org/jupyterhub".
 * @param v2BaseUrl  The Shepard v2 API base URL, e.g. "https://shepard.example.org".
 * @param appId      The FileReference appId (UUID v7).
 * @returns          The full JupyterHub launch URL with `?file=` encoded, or
 *                   `null` if any required argument is missing/empty.
 */
export function buildJupyterLaunchUrl(
  hubUrl: string | null | undefined,
  v2BaseUrl: string,
  appId: string,
): string | null {
  if (!hubUrl || !appId) return null;
  const hubBase = hubUrl.replace(/\/$/, "");
  const downloadUrl = buildShepardFileDownloadUrl(v2BaseUrl, appId);
  return `${hubBase}/hub/spawn?file=${encodeURIComponent(downloadUrl)}`;
}
