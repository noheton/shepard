/**
 * UIRULE-NO-MANUAL-IDS — searchable, permission-filtered picker options for the
 * "watch a container" flow on the collection page (WatchedContainersPanel).
 *
 * Replaces the raw "paste the container appId" text field: the user searches a
 * container by name and the appId travels invisibly. The candidate set is the
 * containers of a given kind the caller may Read, instance-wide (a watch can
 * target a container in ANY collection — see the panel's own empty-state copy:
 * watched containers "show live data on this collection page without needing a
 * DataObject reference"). That semantics is why the referenced-containers list
 * (useFetchCollectionContainers) is the WRONG source here — it returns only the
 * containers already referenced by this collection's DataObjects, i.e. exactly
 * the ones you'd never need to add to a watchlist.
 *
 * Source: GET /v2/containers?kind=…&q=… (ContainersV2Rest.list) — paged,
 * permission-filtered, name-searchable. Search-as-you-type is server-side
 * (debounced) per the async v-autocomplete pattern; a fetch error is fail-soft
 * (keeps whatever is cached, so the "advanced: paste appId" fallback still
 * works).
 *
 * Refs: CLAUDE.md `## Always: the user never types an ID — every selection is a
 * searchable picker`; backlog UIRULE-NO-MANUAL-IDS; mirrors
 * useFetchAccessibleUrdfOptions (URDF-FILEREF-PICKER-SEARCHABLE).
 */

import { ContainersApi, type PagedResponse } from "@dlr-shepard/backend-client";
import type { WatchedContainerKind } from "~/composables/context/useWatchedContainers";
import { naturalSort } from "~/utils/naturalSort";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface WatchableContainerOption {
  appId: string;
  name: string;
}

/**
 * WatchedContainerKind (watch-list casing) → the `/v2/containers` `kind` wire
 * token. The tokens are the `ContainerKindHandler.kind()` values on the backend
 * (`FileContainerKindHandler` → "file", `TimeseriesContainerKindHandler` →
 * "timeseries", `StructuredDataContainerKindHandler` → "structured-data").
 */
export const WATCH_KIND_TO_WIRE: Record<WatchedContainerKind, string> = {
  TIMESERIES: "timeseries",
  FILE: "file",
  STRUCTURED_DATA: "structured-data",
};

/** Debounce window for search-as-you-type on the watchable-container picker (ms). */
export const WATCHABLE_CONTAINER_SEARCH_DEBOUNCE_MS = 300;

/**
 * Pure helper — maps raw `/v2/containers` list items to naturally-sorted picker
 * options. Items without an appId are skipped (a container predating the appId
 * column can't be addressed by the watch API, which keys on appId). The name
 * falls back to the appId when absent. Exported for unit testing.
 */
export function mapWatchableContainerOptions(
  items: { appId?: string | null; name?: string | null }[],
): WatchableContainerOption[] {
  const opts = items.flatMap((c): WatchableContainerOption[] =>
    c.appId ? [{ appId: c.appId, name: c.name ?? c.appId }] : [],
  );
  // UIRULE-DROPDOWN-SEARCH-SORT: numeric-aware order ("Bench 2" before "Bench 10").
  return naturalSort(opts, (o) => o.name);
}

/**
 * Search-as-you-type composable for the instance-wide watchable-container
 * picker. `query` drives a debounced server-side search; `options` holds the
 * mapped + naturally-sorted results for the current `kind`. Changing the kind
 * resets the candidate set (cross-kind results must not linger) and re-queries.
 */
export function useFetchWatchableContainerOptions(
  kindInput: MaybeRefOrGetter<WatchedContainerKind>,
) {
  const query = ref("");
  const options = ref<WatchableContainerOption[]>([]);
  const isLoading = ref(false);
  const api = useV2ShepardApi(ContainersApi);

  // Accumulate loaded options within the current kind so an already-selected
  // container never vanishes when a later, narrower query returns a different
  // page (the classic remote-autocomplete "selection disappears" glitch). The
  // cache is dropped whenever the kind changes.
  let cache = new Map<string, WatchableContainerOption>();
  let debounce: ReturnType<typeof setTimeout> | null = null;
  let runId = 0;

  async function run(): Promise<void> {
    const kind = WATCH_KIND_TO_WIRE[toValue(kindInput)];
    const q = query.value.trim();
    const myRun = ++runId;
    isLoading.value = true;
    try {
      const page: PagedResponse = await api.value.listContainers({
        kind,
        q: q.length > 0 ? q : undefined,
        pageSize: 50,
      });
      if (myRun !== runId) return; // superseded by a newer query
      const items = Array.isArray(page?.items) ? page.items : [];
      for (const opt of mapWatchableContainerOptions(items)) {
        cache.set(opt.appId, opt);
      }
      options.value = naturalSort([...cache.values()], (o) => o.name);
    } catch {
      // fail-soft: keep whatever is already cached (the paste fallback stays).
    } finally {
      if (myRun === runId) isLoading.value = false;
    }
  }

  /** Force a fetch now (used to populate on add-form open). */
  function refresh() {
    void run();
  }

  // Query changes → debounced re-query (search-as-you-type).
  watch(query, () => {
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(() => void run(), WATCHABLE_CONTAINER_SEARCH_DEBOUNCE_MS);
  });

  // Kind changes → drop the cross-kind candidate set and re-query immediately.
  watch(
    () => toValue(kindInput),
    () => {
      cache = new Map();
      options.value = [];
      void run();
    },
  );

  onUnmounted(() => {
    if (debounce) clearTimeout(debounce);
  });

  return { query, options, isLoading, refresh };
}
