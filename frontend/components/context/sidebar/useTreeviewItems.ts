import {
  DataObjectApi,
  ResponseError,
  type DataObject,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { mapToTreeviewItem, type TreeviewItem } from "./treeviewItem";
import { useOpenedItems } from "./useOpenedItems";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): the per-treeview-item DataObject
 * lookup must route through the v2 appId-keyed endpoint
 * `GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`.
 * Pre-fix this composable hit the generated v1 `getDataObject({collectionId,
 * dataObjectId})` expecting numeric Neo4j longs — post-Neo4j-reset Collections
 * carry UUID v7 only and the v1 client 404'd on the stringified id, so the
 * sidebar tree silently broke when a deep-link landed mid-tree (the parent
 * walk in `getPathToItem` could not load any item by id).
 *
 * The list call `getAllDataObjects` stays on v1 — it's not in this BUG's
 * scope. NOTE: the v1 list endpoint
 * `DataObjectRest.getAllDataObjects(@PathParam Long collectionId, ...)`
 * declares a primitive-Long path param, so a UUID-only Collection will 400
 * at the JAX-RS binding before the service ever runs. That means this
 * composable's mid-tree single-item walk is fixed by -005 but the initial
 * tree load remains broken for UUID-only Collections until the v2 list
 * endpoint grows `parentId`/`predecessorId` support (filed as
 * **BUG-COLL-APPID-ROUTE-006** in aidocs/16).
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchDataObjectV2(
  collectionId: number,
  dataObjectId: number,
): Promise<DataObject> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(String(collectionId))}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(url, { headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  // DataObjectDetailV2IO extends DataObjectIO — treeview item mapping only
  // reads `id`, `name`, `parentId`, `childrenIds`, all of which are present.
  return (await resp.json()) as DataObject;
}

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  // BUG-COLL-APPID-ROUTE-001: route ids are strings; cast at boundary so
  // the typed-number v1 client signatures still compile. UUID v7 flows
  // through to the wire intact and 404s cleanly on v1 paths.
  const cid = () => routeParams.value.collectionId as unknown as number;
  const did = () => routeParams.value.dataObjectId as unknown as number | undefined;
  const dataObjectApi = useShepardApi(DataObjectApi);
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  const { openedTreeviewItems, addOpen, collapseItem } = useOpenedItems();

  async function fetchTreeviewItems(collectionId: number) {
    await dataObjectApi.value
      .getAllDataObjects({ collectionId, parentId: -1 })
      .then(response => {
        // initial load of dataobject from a collection - no parents possible
        treeviewItems.value = response
          .map(item => mapToTreeviewItem(item))
          .sort((itemA, itemB) => itemA.id - itemB.id);
        // instead of sorting by 'createdAt' we can sort the treeview items by ID
      })
      .catch(error => {
        treeviewItems.value = undefined;
        handleError(error, "getAllDataObjects");
      });
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
    const parentIds = await getPathToItem(cid(), itemId);
    for (const id of parentIds) {
      await loadChildrenOfItem(id);
    }
    if (openLoadedItems) addOpen(parentIds);
  }

  async function initialLoad() {
    loading.value = true;
    await fetchTreeviewItems(cid());
    const d = did();
    if (d !== undefined) {
      await loadAndExpandUpToItem(d);
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
      await fetchTreeviewItems(cid());
    }
    const d = did();
    if (d !== undefined) {
      await loadAndExpandUpToItem(d);
    }
    loading.value = false;
  });

  async function refreshItems() {
    loading.value = true;
    await fetchTreeviewItems(cid());
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

    const children = await fetchChildrenOfItem(cid(), item.id, item);

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
  // BUG-COLL-APPID-ROUTE-005: route through v2 raw fetch — the v1 generated
  // client 404s on UUID-shaped collection/dataObject ids post-reset.
  return fetchDataObjectV2(collectionId, dataObjectId)
    .then(response => mapToTreeviewItem(response, parentItem))
    .catch(error => {
      if (error instanceof ResponseError) {
        handleError(error, "fetchTreeviewItem");
      }
      return undefined;
    });
}

async function fetchChildrenOfItem(
  collectionId: number,
  parentId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem[] | undefined> {
  return useShepardApi(DataObjectApi)
    .value.getAllDataObjects({
      parentId,
      collectionId,
    })
    .then(response =>
      response
        .map(item => mapToTreeviewItem(item, parentItem))
        .sort((itemA, itemB) => itemA.id - itemB.id),
    )
    .catch(error => {
      if (error instanceof ResponseError) {
        handleError(error, "fetchChildrenOfItem");
      }
      return undefined;
    });
}
