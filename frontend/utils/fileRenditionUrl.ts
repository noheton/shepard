/**
 * TIFF-PREVIEW-SUPPORT — small pure helpers for the file-reference detail
 * page's inline image quick-look. Browsers cannot render `image/tiff`
 * natively, so a TIFF-backed singleton FileReference's inline preview must
 * request the `?rendition=png` transcode on
 * `GET /v2/references/{appId}/content` (see `FileReferenceKindHandler`)
 * instead of the raw content URL. Every other image format (png, jpg, gif,
 * bmp, webp) is already browser-renderable and keeps using the raw URL
 * unchanged.
 *
 * Extracted from the file-reference detail page's inline `computed()`s so
 * the URL-building logic is unit-testable without mounting the full Nuxt
 * page (auth composables, route params, …).
 */

/** True when `filename` ends in `.tif` or `.tiff` (case-insensitive). */
export function isTiffFilename(filename: string | null | undefined): boolean {
  if (!filename) return false;
  const lower = filename.toLowerCase();
  return lower.endsWith(".tif") || lower.endsWith(".tiff");
}

/**
 * Build the URL the inline image preview should request for a singleton
 * FileReference's content: the raw content URL unchanged for
 * browser-renderable formats, or the same URL with `?rendition=png`
 * appended for a TIFF source.
 *
 * @param contentUrl the base `GET .../content` URL (no query string
 *   assumed, but appends correctly either way).
 * @param filename the original filename (or reference name) used to detect
 *   TIFF-ness by extension.
 */
export function buildInlineImageContentUrl(
  contentUrl: string,
  filename: string | null | undefined,
): string {
  if (!isTiffFilename(filename)) return contentUrl;
  const sep = contentUrl.includes("?") ? "&" : "?";
  return `${contentUrl}${sep}rendition=png`;
}
