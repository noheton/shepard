import { CollectionWatchesApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

/**
 * CW1 — Collection watching composable.
 *
 * Wraps POST / DELETE /v2/collections/{collectionAppId}/watches
 * and GET /v2/collections/{collectionAppId}/watches/me.
 *
 * Usage:
 *   const { isWatching, loading, toggle } = useCollectionWatch(collectionAppId);
 *
 * @param collectionAppId  Ref or plain string of the Collection's appId.
 *                         When the value is null/undefined, all methods are no-ops.
 */
export function useCollectionWatch(collectionAppId: Ref<string | null | undefined> | string | null | undefined) {
  const api = useV2ShepardApi(CollectionWatchesApi);

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
      await api.value.getMyWatch({ collectionAppId: appId });
      isWatching.value = true;
    } catch {
      // 404 means not watching; treat any error as not watching.
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
