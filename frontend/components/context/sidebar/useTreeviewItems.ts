import { DataObjectApi, ResponseError } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { mapToTreeviewItem, type TreeviewItem } from "./treeviewItem";
import { useOpenedItems } from "./useOpenedItems";
import type { DataObject } from "@dlr-shepard/backend-client";

// BUG-COLL-APPID-ROUTE-006: resolve the v2 base URL the same way other
// composables (useFetchPredecessorChain, useContainerReferencedByCollections)
// do — strip the /shepard/api suffix and use the host directly.
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * BUG-COLL-APPID-ROUTE-006 — fetch a page of DataObjects from the v2 list
 * endpoint using the appId-based filter.
 *
 * The v1 list endpoint declares a primitive-Long path param for the
 * collectionId, so a UUID-only Collection causes a 400 at the JAX-RS binding
 * layer before the service ever runs. This helper calls the v2 endpoint
 * directly, which accepts a string collectionAppId (UUID v7 or numeric string)
 * and now supports the {@code ?parentAppId=} filter added in this bug fix.
 *
 * @param collectionAppId  String form of the collection id (UUID v7 or legacy numeric string).
 * @param parentAppId      "NONE" for root-level items; a DataObject appId string for children.
 */
async function fetchTreeviewItemsV2(
  collectionAppId: string,
  parentAppId: string,
): Promise<DataObject[]> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) return [];
  const url =
    `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/data-objects` +
    `?parentAppId=${encodeURIComponent(parentAppId)}&size=200`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!resp.ok) return [];
  const json: { items?: DataObject[]; data?: DataObject[] } | DataObject[] =
    await resp.json();
  // The v2 list endpoint returns a JSON array directly (DataObject[]).
  return Array.isArray(json)
    ? json
    : ((json as { items?: DataObject[] }).items ?? []);
}

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  const { openedTreeviewItems, addOpen, collapseItem } = useOpenedItems();

  // BUG-COLL-APPID-ROUTE-006: use the v2 endpoint so UUID-only Collections
  // do not 400 at the JAX-RS primitive-Long binding in v1.
  async function fetchTreeviewItems(collectionId: number) {
    try {
      const items = await fetchTreeviewItemsV2(String(collectionId), "NONE");
      // initial load of dataobjects from a collection — no parents possible
      treeviewItems.value = items
        .map(item => mapToTreeviewItem(item))
        .sort((itemA, itemB) => (itemA.id ?? 0) - (itemB.id ?? 0));
      // instead of sorting by 'createdAt' we can sort the treeview items by ID
    } catch (error) {
      treeviewItems.value = undefined;
      handleError(error, "fetchTreeviewItemsV2 (root)");
    }
  }

  /**
   * @param openLoadedItems will only load and not expand items if disabled, for example to preserve opened state after refreshing
   */
  async function loadAndExpandUpToItem(
    itemId: number,
    openLoadedItems: boolean = true,
  ) {
    const itemWithPath = getItemWithPathIfLoaded(itemId);
    if (itemWithPath) {
      if (openLoadedItems) addOpen(itemWithPath.pathFromRoot);
      return;
    }
    const parentIds = await getPathToItem(
      routeParams.value.collectionId,
      itemId,
    );
    for (const id of parentIds) {
      await loadChildrenOfItem(id);
    }
    if (openLoadedItems) addOpen(parentIds);
  }

  async function initialLoad() {
    loading.value = true;
    await fetchTreeviewItems(routeParams.value.collectionId);
    if (routeParams.value.dataObjectId) {
      await loadAndExpandUpToItem(routeParams.value.dataObjectId);
    }
    loading.value = false;
  }

  initialLoad();

  watch(routeParams, async (_, oldParams) => {
    loading.value = true;
    if (
      routeParams.value.collectionId &&
      oldParams.collectionId !== routeParams.value.collectionId
    ) {
      await fetchTreeviewItems(routeParams.value.collectionId);
    }
    if (routeParams.value.dataObjectId) {
      await loadAndExpandUpToItem(routeParams.value.dataObjectId);
    }
    loading.value = false;
  });

  async function refreshItems() {
    loading.value = true;
    await fetchTreeviewItems(routeParams.value.collectionId);
    for (const openedItem of openedTreeviewItems.value) {
      await loadAndExpandUpToItem(openedItem, false);
      await loadChildrenOfItem(openedItem);
    }
    loading.value = false;
  }

  /**
   * Searches for item in the already loaded tree
   *
   * @param itemId Id of the item to be found
   * @returns The item and the path from the root to the item (excluding the id of the item itself). Undefined if the item can't be found in the tree.
   */
  function getItemWithPathIfLoaded(
    itemId: number,
  ): { item: TreeviewItem; pathFromRoot: number[] } | undefined {
    function getTreeviewItemInSubtree(
      dataObjectId: number,
      dataObjectItemList: TreeviewItem[],
    ): { item: TreeviewItem; pathFromRoot: number[] } | undefined {
      for (const item of dataObjectItemList) {
        if (item.id === dataObjectId) {
          return { item, pathFromRoot: [] };
        }
        if (item.children) {
          const found = getTreeviewItemInSubtree(dataObjectId, item.children);
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
    return getTreeviewItemInSubtree(itemId, treeviewItems.value);
  }

  /**
   * Loads children of item and adds them to the tree.
   *
   * @param itemId Id of the item with children to expand (must be already loaded)
   */
  async function loadChildrenOfItem(itemId: number) {
    const item = getItemWithPathIfLoaded(itemId)?.item;

    if (!item) return;
    if (!item.childrenIds || item.childrenIds.length === 0) {
      item.children = undefined;
      return;
    }
    if (item.children?.length) return;

    const children = await fetchChildrenOfItem(
      routeParams.value.collectionId,
      item.id,
      item,
    );

    if (!children) return;

    item.children = children;
    item.childrenIds = children.map(c => c.id);
  }

  /**
   * @returns List of numbers representing the path to the item (excluding the id of the item itself)
   */
  async function getPathToItem(
    collectionId: number,
    itemId: number,
  ): Promise<number[]> {
    async function getPathFromRootTo(currentPath: number[]): Promise<number[]> {
      const currentRoot = currentPath[0];
      if (currentRoot === undefined) {
        return [];
      }
      const currentRootItemWithPath = getItemWithPathIfLoaded(currentRoot);
      if (currentRootItemWithPath) {
        return [...currentRootItemWithPath.pathFromRoot, ...currentPath];
      }

      const currentRootItem = await fetchTreeviewItem(
        collectionId,
        currentRoot,
        undefined,
      );

      if (!currentRootItem?.parentId) return currentPath;

      // Abort if there is a parent/child cycle
      if (currentPath.some(id => id === currentRootItem.parentId)) return [];

      return await getPathFromRootTo([
        currentRootItem.parentId,
        ...currentPath,
      ]);
    }

    const activeChild = await fetchTreeviewItem(
      collectionId,
      itemId,
      undefined,
    );

    if (!activeChild?.parentId) return [];
    return getPathFromRootTo([activeChild.parentId]);
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loading,
    loadChildrenOfItem,
    refreshItems,
    collapseItem,
  };
};

async function fetchTreeviewItem(
  collectionId: number,
  dataObjectId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem | undefined> {
  return useShepardApi(DataObjectApi)
    .value.getDataObject({
      collectionId,
      dataObjectId,
    })
    .then(response => mapToTreeviewItem(response, parentItem))
    .catch(error => {
      if (error instanceof ResponseError) {
        handleError(error, "fetchTreeviewItem");
      }
      return undefined;
    });
}

// BUG-COLL-APPID-ROUTE-006: swap the child-expansion call to v2 so that
// both the initial tree load and recursive child expansion work for
// UUID-only Collections (v1 getAllDataObjects 400s on UUID path params).
async function fetchChildrenOfItem(
  collectionId: number,
  parentId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem[] | undefined> {
  try {
    const items = await fetchTreeviewItemsV2(
      String(collectionId),
      String(parentId),
    );
    return items
      .map(item => mapToTreeviewItem(item, parentItem))
      .sort((itemA, itemB) => (itemA.id ?? 0) - (itemB.id ?? 0));
  } catch (error) {
    if (error instanceof ResponseError) {
      handleError(error, "fetchChildrenOfItem");
    }
    return undefined;
  }
}
