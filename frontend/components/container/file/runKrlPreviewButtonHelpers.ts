/**
 * KRL-INTERPRETER-06 — pure helpers for the RunKrlPreviewButton.
 *
 * Extracted so unit tests can import without mounting Vue / Vuetify.
 */

/**
 * `.src` is the KUKA Robot Language source-file extension. Case-insensitive.
 */
export function isKrlSrcFile(name: string | undefined | null): boolean {
  if (!name) return false;
  return name.toLowerCase().endsWith(".src");
}
