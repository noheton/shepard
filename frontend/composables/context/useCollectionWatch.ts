import { CollectionWatchesApi, MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

/**
 * CW1 — Collection watching composable.
 *
 * Wraps:
 *   GET  /v2/collections/{collectionAppId}/watches          — list watchers (200)
 *   POST /v2/collections/{collectionAppId}/watches          — start watching
 *   DEL  /v2/collections/{collectionAppId}/watches/me       — stop watching
 *
 * UI-005: refresh() previously called GET /watches/me which returns 404 when
 * the caller isn't watching. The frontend semantically handled that as
 * "not watching", but the browser still logged the 404 as a network error on
 * every collection landing page — console pollution for every researcher,
 * every collection. Modern browsers log any 4xx on fetch to the console
 * regardless of try/catch, so the only fix is to avoid producing the 404.
 *
 * We now call the list endpoint (always 200 when the caller has Read on the
 * collection — which is true on the collection landing page by construction)
 * and check membership by username. The `/me` endpoint stays untouched on the
 * backend: it has a separate legitimate use (checking status on a collection
 * you've lost Read access to).
 *
 * Usage:
 *   const { isWatching, loading, toggle } = useCollectionWatch(collectionAppId);
 *
 * @param collectionAppId  Ref or plain string of the Collection's appId.
 *                         When the value is null/undefined, all methods are no-ops.
 */

// Module-level singleton — the caller's username is stable for the session
// lifetime and doesn't need to be re-fetched per collection.
let _usernameCache: Promise<string | null> | null = null;

function resolveUsername(meApi: { value: { getMe: () => Promise<{ username: string }> } }): Promise<string | null> {
  if (_usernameCache) return _usernameCache;
  _usernameCache = meApi.value
    .getMe()
    .then((me) => me.username ?? null)
    .catch(() => {
      // Failed to resolve username — invalidate cache so next call retries,
      // and degrade to "not watching" rather than crashing the page.
      _usernameCache = null;
      return null;
    });
  return _usernameCache;
}

/**
 * Test-only hook to reset the module-singleton username cache between specs.
 * Not exported through any public surface; the unit test imports it directly.
 */
export function _resetUsernameCacheForTest() {
  _usernameCache = null;
}

export function useCollectionWatch(collectionAppId: Ref<string | null | undefined> | string | null | undefined) {
  const api = useV2ShepardApi(CollectionWatchesApi);
  const meApi = useV2ShepardApi(MeApi);

  const appIdRef = isRef(collectionAppId)
    ? collectionAppId
    : ref(collectionAppId);

  const isWatching = ref(false);
  const loading = ref(false);

  async function refresh() {
    const appId = appIdRef.value;
    if (!appId) {
      isWatching.value = false;
      return;
    }
    loading.value = true;
    try {
      const [watchers, username] = await Promise.all([
        api.value.listWatchers({ collectionAppId: appId }),
        resolveUsername(meApi),
      ]);
      if (!username) {
        isWatching.value = false;
        return;
      }
      isWatching.value = watchers.some((w) => w.username === username);
    } catch {
      // List endpoint returned non-2xx (403 = lost Read on collection,
      // collection deleted, etc.). Treat as not watching — same as the
      // previous /me-404 behaviour.
      isWatching.value = false;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Toggle the watch state. If currently watching, unwatch; otherwise watch.
   * Optimistic update — reverts on failure.
   */
  async function toggle() {
    const appId = appIdRef.value;
    if (!appId) return;

    const wasWatching = isWatching.value;
    isWatching.value = !wasWatching;
    loading.value = true;
    try {
      if (wasWatching) {
        await api.value.unwatch({ collectionAppId: appId });
      } else {
        await api.value.watch({ collectionAppId: appId });
      }
    } catch (error) {
      // Revert on failure.
      isWatching.value = wasWatching;
      handleError(error, wasWatching ? "stopping watch" : "starting watch");
    } finally {
      loading.value = false;
    }
  }

  // Reload when the appId changes.
  watch(appIdRef, () => refresh(), { immediate: true });

  return { isWatching, loading, refresh, toggle };
}
