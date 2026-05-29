import { DataObjectApi, DataObjectV2Api, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface PagedDataObjectsOptions {
  collectionId: number;
  collectionAppId: Ref<string | null>;
  name: Ref<string>;
  status?: Ref<string | undefined>;
  page: Ref<number>;
  pageSize?: number;
  includeTimeBounds?: Ref<boolean>;
}

export interface PagedDataObjectsResult {
  items: Ref<DataObjectListItemV2[]>;
  totalItems: Ref<number | null>;
  loading: Ref<boolean>;
  hasMore: Ref<boolean>;
}

/**
 * Fields projected on each list row — enough for the panel + table
 * without returning every container/annotation/attribute bag.
 * DB-OPT5: trim heavy nested fields server-side to reduce payload.
 */
const DO_LIST_FIELDS = "appId,name,status,createdAt,updatedAt,containerSummary";

/**
 * Server-side paginated DataObject list. Fires a single API call per
 * {collectionAppId, name, status, page} tuple — does NOT exhaustively
 * fetch all pages into memory.  Designed for collections with O(10 000)
 * DataObjects.
 *
 * `totalItems` is null until the count endpoint response lands.
 *
 * When `name`, `status`, or `collectionAppId` changes the page is reset
 * to 0 externally (the panel is responsible for resetting its own `page`
 * ref on filter change).
 */
export function usePagedDataObjects(opts: PagedDataObjectsOptions): PagedDataObjectsResult {
  const { collectionId, collectionAppId, name, page } = opts;
  const pageSize = opts.pageSize ?? 25;
  const includeTimeBounds = opts.includeTimeBounds ?? ref(false);
  const statusFilter = opts.status ?? ref<string | undefined>(undefined);

  const items = ref<DataObjectListItemV2[]>([]);
  const totalItems = ref<number | null>(null);
  const loading = ref(false);
  const hasMore = ref(false);

  const v2Api = useV2ShepardApi(DataObjectV2Api);
  const v1Api = useShepardApi(DataObjectApi);

  async function fetch() {
    const appId = collectionAppId.value;
    const nameFilter = name.value.trim() || undefined;
    const currentPage = page.value;

    loading.value = true;
    try {
      if (appId) {
        const { items: fetched, total: fetchedTotal } = await v2Api.value.listDataObjectsWithCount({
          collectionAppId: appId,
          name: nameFilter,
          status: statusFilter.value,
          page: currentPage,
          size: pageSize,
          include: includeTimeBounds.value ? 'time-bounds' : undefined,
          fields: DO_LIST_FIELDS,
        });
        items.value = fetched;
        totalItems.value = fetchedTotal;
        hasMore.value = fetched.length >= pageSize;
      } else {
        const batch = (await v1Api.value.getAllDataObjects({
          collectionId,
          page: currentPage,
          size: pageSize,
        })) as DataObjectListItemV2[];
        items.value = batch;
        hasMore.value = batch.length >= pageSize;
      }
    } catch (e) {
      handleError(e, "fetchDataObjects");
      items.value = [];
      hasMore.value = false;
    } finally {
      loading.value = false;
    }
  }

  watch([collectionAppId, name, statusFilter, page], () => {
    void fetch();
  }, { immediate: true });

  return { items, totalItems, loading, hasMore };
}
