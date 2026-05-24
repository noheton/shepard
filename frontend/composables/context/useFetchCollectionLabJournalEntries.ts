import {
  CollectionLabJournalEntriesApi,
  type LabJournalEntry,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * UI-020 — bulk fetch every lab-journal entry within a single Collection in
 * one round-trip, replacing the per-DataObject N+1 fan-out that previously
 * collapsed under MFFD-Dropbox (~8500 DOs → 8500 concurrent requests →
 * browser socket exhaustion + thousands of console errors).
 *
 * The endpoint guarantees `dataObjectId` is populated on every entry so the
 * caller can group client-side via {@link groupByDataObjectId}.
 */
export function useFetchCollectionLabJournalEntries(collectionAppId: Ref<string | null>) {
  const entries = ref<LabJournalEntry[] | undefined>(undefined);
  const isLoading = ref(false);
  const api = useV2ShepardApi(CollectionLabJournalEntriesApi);

  async function fetch(appId: string) {
    isLoading.value = true;
    try {
      entries.value = await api.value.listCollectionLabJournalEntries({
        collectionAppId: appId,
      });
    } catch (e) {
      handleError(e as ResponseError, "listCollectionLabJournalEntries");
      entries.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    collectionAppId,
    appId => {
      if (appId) void fetch(appId);
    },
    { immediate: true },
  );

  return { entries, isLoading, refetch: fetch };
}

/**
 * Group lab journal entries by their {@code dataObjectId}, preserving the
 * input order (which the backend hands back as createdAt DESC).
 *
 * Exported for unit-testing in isolation from the network call.
 */
export function groupByDataObjectId(
  entries: ReadonlyArray<LabJournalEntry>,
): Map<number, LabJournalEntry[]> {
  const out = new Map<number, LabJournalEntry[]>();
  for (const e of entries) {
    const key = e.dataObjectId;
    const bucket = out.get(key);
    if (bucket) bucket.push(e);
    else out.set(key, [e]);
  }
  return out;
}
