import { DataObjectApi, DataObjectV2Api, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export interface PagedDataObjectsOptions {
  collectionId: number;
  collectionAppId: Ref<string | null>;
  name: Ref<string>;
  page: Ref<number>;
  pageSize?: number;
  /** When true, each item includes timeBoundsStart / timeBoundsEnd (2 extra DB round-trips). */
  includeTimeBounds?: boolean;
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
  const pageSize = opts.pageSize ?? 25;
  const includeTimeBounds = opts.includeTimeBounds ?? false;

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
      let batch: DataObjectListItemV2[];
      if (appId) {
        batch = await v2Api.value.listDataObjects({
          collectionAppId: appId,
          name: nameFilter,
          page: currentPage,
          size: pageSize,
          include: includeTimeBounds ? 'time-bounds' : undefined,
        });
      } else {
        batch = (await v1Api.value.getAllDataObjects({
          collectionId,
          page: currentPage,
          size: pageSize,
        })) as DataObjectListItemV2[];
      }
      items.value = batch;
      hasMore.value = batch.length >= pageSize;
    } catch (e) {
      handleError(e, "fetchDataObjects");
      items.value = [];
      hasMore.value = false;
    } finally {
      loading.value = false;
    }
  }

  watch([collectionAppId, name, page], () => {
    void fetch();
  }, { immediate: true });

  return { items, totalItems, loading, hasMore };
}
