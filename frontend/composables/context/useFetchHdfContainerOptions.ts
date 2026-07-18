/**
 * UIRULE-NO-MANUAL-IDS — searchable, permission-filtered picker options for the
 * "HDF Container" field of the Add-HDF-Reference dialog (HdfReferencesPane).
 *
 * Replaces the raw "paste the UUID v7 of the HdfContainer" text field: the user
 * searches an HDF container by name and its appId travels invisibly. The
 * candidate set is every HDF container the caller may Read, instance-wide — an
 * HdfReference may point at any readable HdfContainer, not only one in the
 * current collection (same reasoning as the watch-container picker; the
 * referenced-containers list would be the wrong, over-narrow source).
 *
 * Source: GET /v2/containers?kind=hdf&q=… (ContainersV2Rest.list dispatched to
 * the hdf5 plugin's HdfContainerKindHandler, whose kind() token is "hdf") —
 * paged, permission-filtered, name-searchable. Search-as-you-type is server-side
 * (debounced) per the async v-autocomplete pattern; a fetch error is fail-soft
 * (keeps whatever is cached, so the "advanced: paste appId" fallback still
 * works, and the picker degrades to empty rather than crashing when the HDF
 * feature toggle / plugin is off).
 *
 * Refs: CLAUDE.md `## Always: the user never types an ID — every selection is a
 * searchable picker`; backlog UIRULE-NO-MANUAL-IDS; mirrors
 * useFetchWatchableContainerOptions (the sibling container picker).
 */

import { ContainersApi, type PagedResponse } from "@dlr-shepard/backend-client";
import { naturalSort } from "~/utils/naturalSort";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface HdfContainerOption {
  appId: string;
  name: string;
}

/**
 * The `/v2/containers` `kind` wire token for HDF containers. This is the
 * `HdfContainerKindHandler.kind()` value on the backend (hdf5 plugin). A
 * mismatch here would send the picker an unknown `kind` (400 → empty list).
 */
export const HDF_CONTAINER_WIRE_KIND = "hdf";

/** Debounce window for search-as-you-type on the HDF-container picker (ms). */
export const HDF_CONTAINER_SEARCH_DEBOUNCE_MS = 300;

/**
 * Pure helper — maps raw `/v2/containers` list items to naturally-sorted picker
 * options. Items without an appId are skipped (a container predating the appId
 * column can't be addressed by the hdf-reference API, which keys on appId). The
 * name falls back to the appId when absent. Exported for unit testing.
 */
export function mapHdfContainerOptions(
  items: { appId?: string | null; name?: string | null }[],
): HdfContainerOption[] {
  const opts = items.flatMap((c): HdfContainerOption[] =>
    c.appId ? [{ appId: c.appId, name: c.name ?? c.appId }] : [],
  );
  // UIRULE-DROPDOWN-SEARCH-SORT: numeric-aware order ("Run 2" before "Run 10").
  return naturalSort(opts, (o) => o.name);
}

/**
 * Search-as-you-type composable for the instance-wide HDF-container picker.
 * `query` drives a debounced server-side search; `options` holds the mapped +
 * naturally-sorted results. `refresh()` forces a fetch now (used to populate on
 * dialog open).
 */
export function useFetchHdfContainerOptions() {
  const query = ref("");
  const options = ref<HdfContainerOption[]>([]);
  const isLoading = ref(false);
  const api = useV2ShepardApi(ContainersApi);

  // Accumulate loaded options so an already-selected container never vanishes
  // when a later, narrower query returns a different page (the classic
  // remote-autocomplete "selection disappears" glitch).
  const cache = new Map<string, HdfContainerOption>();
  let debounce: ReturnType<typeof setTimeout> | null = null;
  let runId = 0;

  async function run(): Promise<void> {
    const q = query.value.trim();
    const myRun = ++runId;
    isLoading.value = true;
    try {
      const page: PagedResponse = await api.value.listContainers({
        kind: HDF_CONTAINER_WIRE_KIND,
        q: q.length > 0 ? q : undefined,
        pageSize: 50,
      });
      if (myRun !== runId) return; // superseded by a newer query
      const items = Array.isArray(page?.items) ? page.items : [];
      for (const opt of mapHdfContainerOptions(items)) {
        cache.set(opt.appId, opt);
      }
      options.value = naturalSort([...cache.values()], (o) => o.name);
    } catch {
      // fail-soft: keep whatever is already cached (the paste fallback stays,
      // and the picker degrades to empty when the HDF plugin/toggle is off).
    } finally {
      if (myRun === runId) isLoading.value = false;
    }
  }

  /** Force a fetch now (used to populate on dialog open). */
  function refresh() {
    void run();
  }

  // Query changes → debounced re-query (search-as-you-type).
  watch(query, () => {
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(() => void run(), HDF_CONTAINER_SEARCH_DEBOUNCE_MS);
  });

  onUnmounted(() => {
    if (debounce) clearTimeout(debounce);
  });

  return { query, options, isLoading, refresh };
}
