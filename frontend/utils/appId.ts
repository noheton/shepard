/**
 * Canonical appId helpers — shared truncation + clipboard logic so every
 * table that surfaces a UUID v7 uses the same `<head>…<tail>` shape.
 *
 * Background: II3 (ui-scrutinizer-2026-05-30). Pre-existing `truncateAppId`
 * implementations diverged — `sceneGraphsLanding.ts` used 8…4,
 * `GitCredentialsPane.vue` used 12…. Settling on 8…4 (time-ordered prefix
 * + uniqueness tail) keeps the SCENEGRAPH-LIST-1 shape and keeps cells
 * narrow on dense tables.
 */

/**
 * Truncate a UUID v7 appId to `<head>…<tail>` form for tabular display.
 * Keeps the first 8 chars (the time-ordered prefix that's diagnostic) +
 * the last 4 chars (uniqueness tail). Strings short enough already pass
 * through unchanged.
 */
export function truncateAppId(appId: string): string {
  if (!appId) return "";
  return appId.length > 13 ? `${appId.slice(0, 8)}…${appId.slice(-4)}` : appId;
}

/**
 * Best-effort write to the user's clipboard. Returns true on success,
 * false when the clipboard API is unavailable (e.g. http test contexts,
 * Safari without user gesture). Callers that want to surface a toast
 * should do so based on the return value — this helper never throws.
 */
export async function copyAppIdToClipboard(appId: string): Promise<boolean> {
  if (!appId) return false;
  try {
    if (typeof navigator === "undefined" || !navigator.clipboard) return false;
    await navigator.clipboard.writeText(appId);
    return true;
  } catch {
    return false;
  }
}
