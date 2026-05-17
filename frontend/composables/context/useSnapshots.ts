import {
  CollectionSnapshotApi,
  SnapshotApi,
  type SnapshotDiffIO,
  type SnapshotIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

/**
 * UI1a — composable that manages snapshots for a Collection.
 *
 * Provides reactive snapshot list, loading state, and mutations
 * (create, delete, diff) against the V2b/V2e snapshot endpoints.
 *
 * @param collectionAppId - reactive ref of the collection's appId (may be null
 *   before the collection is loaded).
 */
export function useSnapshots(collectionAppId: Ref<string | null>) {
  const snapshots = ref<SnapshotIO[]>([]);
  const isLoading = ref(false);
  const isSaving = ref(false);

  const collectionSnapshotApi = useV2ShepardApi(CollectionSnapshotApi);
  const snapshotApi = useV2ShepardApi(SnapshotApi);

  function refresh() {
    const appId = collectionAppId.value;
    if (!appId) return;

    isLoading.value = true;
    collectionSnapshotApi.value
      .listSnapshots(appId)
      .then(result => {
        snapshots.value = result;
      })
      .catch(error => {
        handleError(error, "listSnapshots");
        snapshots.value = [];
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  async function createSnapshot(
    name: string,
    description?: string | null,
  ): Promise<SnapshotIO | null> {
    const appId = collectionAppId.value;
    if (!appId) return null;

    isSaving.value = true;
    try {
      const created = await collectionSnapshotApi.value.createSnapshot(appId, {
        name,
        description,
      });
      refresh();
      return created;
    } catch (error) {
      handleError(error, "createSnapshot");
      return null;
    } finally {
      isSaving.value = false;
    }
  }

  async function deleteSnapshot(snapshotAppId: string): Promise<boolean> {
    isSaving.value = true;
    try {
      await snapshotApi.value.deleteSnapshot(snapshotAppId);
      refresh();
      return true;
    } catch (error) {
      handleError(error, "deleteSnapshot");
      return false;
    } finally {
      isSaving.value = false;
    }
  }

  async function diffSnapshots(
    aAppId: string,
    bAppId: string,
  ): Promise<SnapshotDiffIO | null> {
    try {
      return await snapshotApi.value.diffSnapshots(aAppId, bAppId);
    } catch (error) {
      handleError(error, "diffSnapshots");
      return null;
    }
  }

  // Initial fetch when appId is available.
  if (collectionAppId.value) {
    refresh();
  }

  // Refetch when the collection changes.
  watch(collectionAppId, newAppId => {
    snapshots.value = [];
    if (newAppId) {
      refresh();
    }
  });

  return { snapshots, isLoading, isSaving, createSnapshot, deleteSnapshot, diffSnapshots, refresh };
}
