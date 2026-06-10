import {
  DataObjectV2Api,
  type DataObjectListItemV2,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { buildTreeviewItems, type TreeviewItem } from "./treeviewItem";
import { useOpenedItems } from "./useOpenedItems";

/**
 * V2-SWEEP Wave 1 (2026-06-10) — the sidebar tree is v2-only and
 * appId-native.
 *
 * History: BUG-COLL-APPID-ROUTE-005/-006 patched the v1
 * `getAllDataObjects({collectionId: Long, parentId})` list calls with a
 * numeric-collection-id gate resolved from the loaded v2 Collection's `.id`.
 * On appId-only data the gate never opened → "sidebar broken in all
 * projects" (operator, 2026-06-10).
 *
 * Fix: load the whole tree from the v2 appId-keyed list
 * `GET /v2/collections/{collectionAppId}/data-objects` (paged, exhaustive,
 * `fields=` trimmed to the tree's needs) and materialise it client-side
 * via `buildTreeviewItems`. No numeric gate, no v1 helper, no lazy child
 * fetches — child linkage already arrives in the list rows. The collection
 * route param goes straight on the v2 path: the backend's EntityIdResolver
 * accepts a UUID v7 or a legacy numeric string transparently.
 */

const PAGE_SIZE = 200;
// Safety valve: 100 pages × 200 = 20k DataObjects per collection. Beyond
// that the sidebar tree is the wrong affordance anyway (see UX-auditor
// findings on virtualization); log and render what we have.
const MAX_PAGES = 100;

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  const dataObjectV2Api = useV2ShepardApi(DataObjectV2Api);
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  // Distinct from `loading` so the template can render an explicit error
  // state instead of an infinite spinner when the v2 list call fails.
  const loadError = ref<boolean>(false);
  const { openedTreeviewItems, addOpen, collapseItem } = useOpenedItems();

  async function fetchAllRows(
    collectionId: string,
  ): Promise<DataObjectListItemV2[]> {
    const rows: DataObjectListItemV2[] = [];
    for (let page = 0; page < MAX_PAGES; page++) {
      const batch = await dataObjectV2Api.value.listDataObjects({
        collectionAppId: collectionId,
        // DB-OPT5 trim: the tree only needs identity + linkage. `id` is the
        // numeric Neo4j id used purely for client-side tree assembly (and
        // the SIDEBAR-V2-CREATE v1-dialog exception); it never reaches a
        // route or link.
        fields: "id,appId,name,parentId,childrenIds",
        page,
        size: PAGE_SIZE,
      });
      rows.push(...batch);
      if (batch.length < PAGE_SIZE) break;
    }
    return rows;
  }

  async function fetchTreeviewItems(collectionId: string) {
    loadError.value = false;
    try {
      const rows = await fetchAllRows(collectionId);
      treeviewItems.value = buildTreeviewItems(rows);
    } catch (error) {
      // Render an empty tree with an explicit error sentinel rather than
      // leaving treeviewItems undefined (which the template reads as
      // "still loading" and spins indefinitely).
      treeviewItems.value = [];
      loadError.value = true;
      handleError(error, "listDataObjects");
    }
  }

  /**
   * Find an item and the appId path from the root to it (excluding the item
   * itself). Pure in-memory walk — the tree is fully materialised.
   *
   * @param itemId appId (UUID v7) or, for legacy numeric deep-links, the
   *   stringified numeric id (matched against `numericId`).
   */
  function getItemWithPathIfLoaded(
    itemId: string,
  ): { item: TreeviewItem; pathFromRoot: string[] } | undefined {
    function walk(
      items: TreeviewItem[],
    ): { item: TreeviewItem; pathFromRoot: string[] } | undefined {
      for (const item of items) {
        if (item.id === itemId || String(item.numericId) === itemId) {
          return { item, pathFromRoot: [] };
        }
        if (item.children) {
          const found = walk(item.children);
          if (found) {
            return {
              item: found.item,
              pathFromRoot: [item.id, ...found.pathFromRoot],
            };
          }
        }
      }
      return undefined;
    }

    if (!treeviewItems.value) return undefined;
    return walk(treeviewItems.value);
  }

  /** Expand all ancestors of `itemId` so it is visible in the tree. */
  function expandUpToItem(itemId: string) {
    const itemWithPath = getItemWithPathIfLoaded(itemId);
    if (itemWithPath) addOpen(itemWithPath.pathFromRoot);
  }

  async function initialLoad() {
    loading.value = true;
    await fetchTreeviewItems(routeParams.value.collectionId);
    const did = routeParams.value.dataObjectId;
    if (did !== undefined) expandUpToItem(did);
    loading.value = false;
  }

  initialLoad();

  watch(routeParams, async (_, oldParams) => {
    if (
      routeParams.value.collectionId &&
      oldParams.collectionId !== routeParams.value.collectionId
    ) {
      loading.value = true;
      await fetchTreeviewItems(routeParams.value.collectionId);
      loading.value = false;
    }
    const did = routeParams.value.dataObjectId;
    if (did !== undefined) expandUpToItem(did);
  });

  async function refreshItems() {
    loading.value = true;
    await fetchTreeviewItems(routeParams.value.collectionId);
    loading.value = false;
    // appIds are stable across refreshes — the opened set carries over as-is.
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loading,
    loadError,
    refreshItems,
    collapseItem,
  };
};
