/**
 * useSpatialDataReferencesForDataObject — DataObject-scoped accessor for
 * SpatialDataReference rows.
 *
 * MFFD W7 (GAP-5) lands the first user-visible surface for spatial data:
 * the pane on the DataObject detail page that lists every promoted
 * SpatialDataContainer for that DO. This composable backs that pane.
 *
 * Returns a reactive list of ``SpatialDataReference`` rows. Errors are
 * surfaced to the global notification toast; on failure the list is empty
 * (degraded-but-functional per the fail-soft principle).
 */
import type { SpatialDataReference } from "@dlr-shepard/backend-client";
import { SpatialDataReferenceApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

// BUG-COLL-APPID-ROUTE-007-PAGE: numeric ids accepted as number / Ref / getter
// and resolved at fetch time — the appId-routed DataObject detail page only
// produces the NUMERIC ids this v1 endpoint needs once the v2 entity loads.
export function useSpatialDataReferencesForDataObject(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const references = ref<SpatialDataReference[]>([]);
  const isLoading = ref<boolean>(false);
  const error = ref<string | null>(null);

  function ids(): { collectionId: number; dataObjectId: number } | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    if (collectionId == null || dataObjectId == null) return undefined;
    return { collectionId, dataObjectId };
  }

  async function refresh(): Promise<void> {
    const resolved = ids();
    if (!resolved) return;
    const { collectionId, dataObjectId } = resolved;
    isLoading.value = true;
    error.value = null;
    try {
      const result = await useShepardApi(
        SpatialDataReferenceApi,
      ).value.getAllSpatialDataReferences({
        collectionId,
        dataObjectId,
      });
      references.value = result;
    } catch (err) {
      // Fail-soft: empty list, never surface a 500 to the parent pane.
      handleError(err, "useSpatialDataReferencesForDataObject");
      error.value = err instanceof Error ? err.message : String(err);
      references.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  // Bind a per-DO observer so the pane refreshes when other panes mutate.
  onDataObjectUpdated(refresh);
  // Defer until both ids resolve; re-run on first resolution (appId-route case).
  watch(ids, resolved => {
    if (resolved) refresh();
  }, { immediate: true });

  return {
    references,
    isLoading,
    error,
    refresh,
  };
}
