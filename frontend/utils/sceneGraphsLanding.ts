/**
 * SCENEGRAPH-LIST-1 — pure helpers for the `/scene-graphs` landing page.
 *
 * Extracted out of `pages/scene-graphs/index.vue` so the row-formatting,
 * appId truncation, and three-branch display logic can be unit-tested
 * without mounting Vuetify's `v-data-table-server`. Same shape as the
 * `sectionLanding` + `toolsLanding` helpers.
 */

/**
 * Render an epoch-millis timestamp as a short DD-Mon-YYYY string. Falls back
 * to an em-dash when the value is null/undefined (pre-SCENEGRAPH-LIST-1 rows
 * which lack `createdAt` / `updatedAt`).
 */
export function formatEpochMillis(epoch: number | null | undefined): string {
  if (epoch === null || epoch === undefined) return "—";
  return new Date(epoch).toLocaleDateString("en-UK", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

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
 * Three-branch display resolver for the landing page:
 *
 *   "table"   → there's at least one scene OR a fetch is in flight; show the table card
 *   "help"    → catalogue is empty and no error; show the "No scenes yet" help card
 *   "error"   → list fetch failed; show the error alert (table + help both suppressed)
 *
 * The page also renders the open-by-appId expansion below this branch — that
 * stays visible regardless. Mirrors the three-branch pattern documented in
 * `VideoContainerPage.test.ts`.
 */
export type LandingBranch = "table" | "help" | "error";

export function resolveLandingBranch(
  totalRows: number,
  loading: boolean,
  errorMessage: string | null,
): LandingBranch {
  if (errorMessage !== null && totalRows === 0 && !loading) return "error";
  if (totalRows > 0 || loading) return "table";
  return "help";
}
