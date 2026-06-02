import { DataObjectApi, DataObjectV2Api, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface PagedDataObjectsOptions {
  collectionId: number;
  collectionAppId: Ref<string | null>;
  name: Ref<string>;
  /** When non-empty, filters DataObjects server-side by lifecycle status (e.g. "READY"). */
  status?: Ref<string | "">;
  page: Ref<number>;
  pageSize?: number;
  /** When true, each item includes timeBoundsStart / timeBoundsEnd (2 extra DB round-trips). */
  includeTimeBounds?: boolean;
  /**
   * COLL-TIMELINE-DRILLDOWN-FILTER-1 — annotation filter in the form
   * `<predicateIRI>=<value>` (e.g. `urn:shepard:mffd:process-type=AFP`).
   * When null / empty, no annotation filter is applied.
   */
  annotationFilter?: Ref<string | null>;
  /**
   * COLL-TIMELINE-DRILLDOWN-FILTER-1 — ISO-8601 date lower bound (inclusive).
   * When null / empty, no lower date bound is applied.
   */
  createdAfter?: Ref<string | null>;
  /**
   * COLL-TIMELINE-DRILLDOWN-FILTER-1 — ISO-8601 date upper bound (inclusive).
   * When null / empty, no upper date bound is applied.
   */
  createdBefore?: Ref<string | null>;
}

export interface PagedDataObjectsResult {
  items: Ref<DataObjectListItemV2[]>;
  totalItems: Ref<number | null>;
  loading: Ref<boolean>;
  hasMore: Ref<boolean>;
}

/**
 * Server-side paginated DataObject list. Fires a single API call per
 * {collectionAppId, name, page} triple — does NOT exhaustively fetch all
 * pages into memory.  Designed for collections with O(10 000) DataObjects.
 *
 * `totalItems` is `null` until a full count is available; the UI shows
 * "25 of ?" when the count is unknown (the backend list endpoint does not
 * yet return a total-count header).
 *
 * When `name` or `collectionAppId` changes the page is reset to 0 externally
 * (the panel is responsible for resetting its own `page` ref on filter change).
 */
export function usePagedDataObjects(opts: PagedDataObjectsOptions): PagedDataObjectsResult {
  const { collectionId, collectionAppId, name, page } = opts;
  const status = opts.status;
  const pageSize = opts.pageSize ?? 25;
  const includeTimeBounds = opts.includeTimeBounds ?? false;
  const annotationFilter = opts.annotationFilter;
  const createdAfter = opts.createdAfter;
  const createdBefore = opts.createdBefore;

  // DB-OPT5: explicit ?fields= allow-list of exactly what the collection-detail
  // panel renders. Drops description, attributes, predecessor/successor arrays,
  // and the four deprecated int sibling counts. Backend always returns id/appId/
  // name as identity even when not listed here.
  //
  // Fields actually read by CollectionDataObjectsPanel.vue (Row mapping):
  //  - id, name, status, createdAt
  //  - referenceIds[]  → row.refCount (.length)
  //  - childrenIds[]   → row.childCount (.length)
  //  - incomingIds[]   → row.incomingCount (.length)
  //  - timeseriesCount, fileCount, structuredDataCount (v2 long counts)
  //  - timeBoundsStart, timeBoundsEnd (when ?include=time-bounds)
  const DO_LIST_FIELDS = [
    "id",
    "appId",
    "name",
    "status",
    "createdAt",
    "referenceIds",
    "childrenIds",
    "incomingIds",
    "timeseriesCount",
    "fileCount",
    "structuredDataCount",
    "timeBoundsStart",
    "timeBoundsEnd",
  ].join(",");

  const items = ref<DataObjectListItemV2[]>([]);
  const totalItems = ref<number | null>(null);
  const loading = ref(false);
  const hasMore = ref(false);

  const v2Api = useV2ShepardApi(DataObjectV2Api);
  const v1Api = useShepardApi(DataObjectApi);

  async function fetch() {
    const appId = collectionAppId.value;
    const nameFilter = name.value.trim() || undefined;
    const statusFilter = status?.value || undefined;
    const currentPage = page.value;
    const annotationFilterVal = annotationFilter?.value || undefined;
    const createdAfterVal = createdAfter?.value || undefined;
    const createdBeforeVal = createdBefore?.value || undefined;

    loading.value = true;
    try {
      let batch: DataObjectListItemV2[];
      if (appId) {
        // DB-OPT5: cast bypasses stale generated client that lacks the `fields`
        // param — see FE-BUILD-03 in aidocs/16. Real fix is client regen.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const { items: fetched, total: fetchedTotal } = await v2Api.value.listDataObjectsWithCount({
          collectionAppId: appId,
          name: nameFilter,
          status: statusFilter,
          page: currentPage,
          size: pageSize,
          include: includeTimeBounds ? 'time-bounds' : undefined,
          fields: DO_LIST_FIELDS,
          annotationFilter: annotationFilterVal,
          createdAfter: createdAfterVal,
          createdBefore: createdBeforeVal,
        } as unknown as Parameters<typeof v2Api.value.listDataObjectsWithCount>[0]);
        batch = fetched;
        totalItems.value = fetchedTotal;
      } else {
        batch = (await v1Api.value.getAllDataObjects({
          collectionId,
          page: currentPage,
          size: pageSize,
        })) as DataObjectListItemV2[];
        totalItems.value = null;
      }
      items.value = batch;
      hasMore.value = batch.length >= pageSize;
    } catch (e) {
      handleError(e, "fetchDataObjects");
      items.value = [];
      hasMore.value = false;
      totalItems.value = null;
    } finally {
      loading.value = false;
    }
  }

  // Watch all filter sources as a computed snapshot so Vue can deep-compare.
  // This replaces the earlier status-conditional array which couldn't accommodate
  // the optional annotationFilter / createdAfter / createdBefore refs.
  watch(
    computed(() => ({
      appId: collectionAppId.value,
      name: name.value,
      status: status?.value,
      page: page.value,
      annotationFilter: annotationFilter?.value,
      createdAfter: createdAfter?.value,
      createdBefore: createdBefore?.value,
    })),
    () => {
      void fetch();
    },
    { immediate: true }
  );

  return { items, totalItems, loading, hasMore };
}
