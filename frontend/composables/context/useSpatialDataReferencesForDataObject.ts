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

export function useSpatialDataReferencesForDataObject(
  collectionId: number,
  dataObjectId: number,
) {
  const references = ref<SpatialDataReference[]>([]);
  const isLoading = ref<boolean>(false);
  const error = ref<string | null>(null);

  async function refresh(): Promise<void> {
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
  refresh();

  return {
    references,
    isLoading,
    error,
    refresh,
  };
}
