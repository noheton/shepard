import {
  SnapshotsApi,
  type SnapshotDiff,
  type Snapshot,
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
  const snapshots = ref<Snapshot[]>([]);
  const isLoading = ref(false);
  const isSaving = ref(false);

  // V2-SWEEP-001-CLIENT-REGEN: the regenerated client folds the old
  // CollectionSnapshotApi + SnapshotApi into one SnapshotsApi with
  // request-object params. listSnapshots→listCollectionSnapshots,
  // createSnapshot→createCollectionSnapshot, diffSnapshots→diff.
  const snapshotsApi = useV2ShepardApi(SnapshotsApi);

  function refresh() {
    const appId = collectionAppId.value;
    if (!appId) return;

    isLoading.value = true;
    snapshotsApi.value
      .listCollectionSnapshots({ collectionAppId: appId })
      .then((result: any) => {
        snapshots.value = Array.isArray(result) ? result : (result.items ?? []);
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
  ): Promise<Snapshot | null> {
    const appId = collectionAppId.value;
    if (!appId) return null;

    isSaving.value = true;
    try {
      const created = await snapshotsApi.value.createCollectionSnapshot({
        collectionAppId: appId,
        snapshot: { name, description: description ?? undefined },
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
      await snapshotsApi.value.deleteSnapshot({ snapshotAppId });
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
  ): Promise<SnapshotDiff | null> {
    try {
      return await snapshotsApi.value.diff({ aAppId, bAppId });
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
