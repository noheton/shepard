/**
 * V2-LINKS — resolve a numeric Neo4j collection id to its UUID-v7 `appId`.
 *
 * Some v2 payloads (notably `DataObject`) carry only the numeric
 * `collectionId` of their owning collection, never a `collectionAppId`.
 * Building a `/collections/{appId}` route therefore needs a numeric→appId
 * lookup. Both v2 detail routes (`GET /v2/collections/{appId}`) now reject
 * the numeric id with 404 — so a numeric-id link is a genuine dead link, the
 * exact failure the operator hit on `/collections/367014`.
 *
 * The cheapest correct resolution is the v2 collections LIST, which carries
 * BOTH `id` and `appId` for every collection. We fetch it once, build a
 * module-level numeric→appId cache, and resolve from there. The cache is
 * shared across every component that imports this composable (a row table can
 * resolve hundreds of links off one fetch). Unknown ids trigger a single lazy
 * refresh in case the collection was created after the cache was warmed.
 */
import { CollectionApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { readCollectionAppId } from "~/utils/appId";

// Module-level cache shared across all callers. Keyed by numeric collection id.
const numericToAppId = new Map<number, string>();
let warmed = false;
let inFlight: Promise<void> | null = null;

export function useCollectionAppIdResolver() {
  const collectionApi = useShepardApi(CollectionApi);

  async function warm(): Promise<void> {
    // Never fetch during SSR — backendApiUrl is empty server-side and this is
    // auth-gated personalised data that must load on the client.
    if (!import.meta.client) return;
    if (warmed) return;
    if (inFlight) return inFlight;
    inFlight = (async () => {
      try {
        // Page through all visible collections (size cap is generous; most
        // instances have far fewer than 500 collections).
        const results = await collectionApi.value.getAllCollections({
          page: 0,
          size: 500,
        });
        for (const c of results) {
          const appId = readCollectionAppId(c);
          if (appId) numericToAppId.set(c.id, appId);
        }
        warmed = true;
      } catch {
        // Fail-soft: leave the cache cold; resolve() returns null and callers
        // degrade to a non-navigable label rather than a 404 dead link.
      } finally {
        inFlight = null;
      }
    })();
    return inFlight;
  }

  /**
   * Resolve a numeric collection id to its appId. Warms the cache on first
   * call. Returns null when the collection is not visible to the user or the
   * fetch failed — the caller should then render a non-navigable label rather
   * than a numeric-id route that would 404.
   */
  async function resolve(numericId: number | undefined | null): Promise<string | null> {
    if (numericId == null) return null;
    if (numericToAppId.has(numericId)) return numericToAppId.get(numericId)!;
    await warm();
    return numericToAppId.get(numericId) ?? null;
  }

  /** Synchronous peek — returns the cached appId or null without fetching. */
  function peek(numericId: number | undefined | null): string | null {
    if (numericId == null) return null;
    return numericToAppId.get(numericId) ?? null;
  }

  return { resolve, peek, warm };
}
