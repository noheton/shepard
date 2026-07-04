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

/**
 * V2-LINKS — shared appId accessors for navigation-route construction.
 *
 * Per the CLAUDE.md "frontend builds on /v2/ exclusively" rule, every route,
 * `<NuxtLink :to>`, `<v-btn :to>`, `router.push` / `navigateTo`, `:href` and
 * stored route preference MUST carry the UUID-v7 `appId` — never the numeric
 * Neo4j `id`. The operator-surfaced failure was `/collections/367014`
 * (numeric) → the v2/appId-only sub-fetches 404'd and the page showed
 * "Couldn't load the DataObject tree".
 *
 * KEY FACT: the v2 wire carries BOTH `id` (numeric) AND `appId` (UUID) on
 * every entity, but the v1-generated TypeScript models
 * (`@dlr-shepard/backend-client`) only DECLARE `id`. So `entity.appId` needs a
 * defensive cast. These accessors centralise the cast that was previously
 * inlined across `CollectionSidebarHeader.vue`, `useFetchCollection.ts`,
 * `CollectionDataObjectsPanel.vue`, the container accessors, etc.
 *
 * Each accessor returns `string | null` so the caller can decide what to do
 * when the appId is genuinely absent (the v1-only fallback path) — prefer
 * the appId, fall back to numeric only when there is no other option.
 */
function readAppId(entity: unknown): string | null {
  if (entity == null || typeof entity !== "object") return null;
  const direct = (entity as { appId?: string | null }).appId;
  if (typeof direct === "string" && direct.length > 0) return direct;
  const bag = (
    entity as { additional_properties?: { appId?: string | null } }
  ).additional_properties;
  const fromBag = bag?.appId;
  if (typeof fromBag === "string" && fromBag.length > 0) return fromBag;
  return null;
}

/**
 * appId of a Collection entity (or null if absent / nullish input).
 *
 * Named `read*` rather than `collectionAppId` because Nuxt auto-imports every
 * `utils/` export globally, and `collectionAppId` / `dataObjectAppId` are
 * already widely used as LOCAL computed/ref names across the codebase (15+
 * sites). A bare `collectionAppId` export would shadow those locals at the
 * type level (vue-tsc flags `if (collectionAppId)` as always-truthy). The
 * `read*` prefix keeps the helpers collision-free while reading naturally.
 */
export function readCollectionAppId(collection: unknown): string | null {
  return readAppId(collection);
}

/** appId of a DataObject entity (or null if absent / nullish input). */
export function readDataObjectAppId(dataObject: unknown): string | null {
  return readAppId(dataObject);
}

/** appId of a Container entity (or null if absent / nullish input). */
export function readContainerAppId(container: unknown): string | null {
  return readAppId(container);
}
