import { DataObjectsApi, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * Extracts the total count from the list response headers. The backend exposes
 * the full count via `X-Total-Count`, falling back to the `Content-Range`
 * "unit 0-24/8514" form. Returns null when neither header is present.
 *
 * V2-SWEEP-001-CLIENT-REGEN: the regenerated client dropped the hand-rolled
 * `listDataObjectsWithCount` wrapper, so the header read lives here now, on top
 * of the generated `listDataObjectsRaw`.
 */
function parseTotalCount(headers: Headers): number | null {
  const xTotal = headers.get("X-Total-Count");
  if (xTotal != null) {
    const parsed = parseInt(xTotal, 10);
    if (!isNaN(parsed)) return parsed;
  }
  const contentRange = headers.get("Content-Range");
  if (contentRange != null) {
    const m = /\/(\d+)\s*$/.exec(contentRange);
    if (m?.[1] != null) return parseInt(m[1], 10);
  }
  return null;
}

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

  const v2Api = useV2ShepardApi(DataObjectsApi);

  async function fetch() {
    const appId = collectionAppId.value;
    const nameFilter = name.value.trim() || undefined;
    const statusFilter = status?.value || undefined;
    const currentPage = page.value;

    loading.value = true;
    try {
      // V2-SWEEP Wave 4: v2-only — when the appId hasn't materialised yet,
      // the stringified numeric id goes on the same v2 path (backend
      // EntityIdResolver accepts both shapes). The v1 getAllDataObjects
      // fallback is gone.
      const identifier = appId ?? String(collectionId);
      const raw = await v2Api.value.listDataObjectsRaw({
        collectionAppId: identifier,
        name: nameFilter,
        status: statusFilter,
        page: currentPage,
        pageSize: pageSize,
        include: includeTimeBounds ? 'time-bounds' : undefined,
        fields: DO_LIST_FIELDS,
      });
      const fetchedTotal = parseTotalCount(raw.raw.headers);
      const batch: DataObjectListItemV2[] = await raw.value();
      totalItems.value = fetchedTotal;
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

  const watchSources = status
    ? [collectionAppId, name, status, page] as const
    : [collectionAppId, name, page] as const;
  watch(watchSources, () => {
    void fetch();
  }, { immediate: true });

  return { items, totalItems, loading, hasMore };
}
