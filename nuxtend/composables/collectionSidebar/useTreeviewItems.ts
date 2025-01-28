import { DataObjectApi } from "@dlr-shepard/backend-client";
import {
  mapToTreeviewItem,
  type TreeviewItem,
} from "~/composables/collectionSidebar/treeviewItem";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  const { openedTreeviewItems, addOpen } = useOpenedItems();

  async function fetchTreeviewItems(collectionId: number) {
    await createApiInstance(DataObjectApi)
      .getAllDataObjects({ collectionId, parentId: -1 })
      .then(response => {
        // initial load of dataobject from a collection - no parents possible
        treeviewItems.value = response.map(item => mapToTreeviewItem(item));
      })
      .catch(error => {
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
    if (!item.childrenIds?.length) {
      item.children = [];
    }
    if (item.children?.length) return;

    const children = await fetchChildrenOfItem(
      routeParams.value.collectionId,
      item.id,
      item,
    );

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

      if (!currentRootItem.parentId) return currentPath;

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

    if (!activeChild.parentId) return [];
    return getPathFromRootTo([activeChild.parentId]);
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loading,
    loadChildrenOfItem,
    refreshItems,
  };
};

async function fetchTreeviewItem(
  collectionId: number,
  dataObjectId: number,
  parentItem?: TreeviewItem,
) {
  return mapToTreeviewItem(
    await createApiInstance(DataObjectApi).getDataObject({
      collectionId,
      dataObjectId,
    }),
    parentItem,
  );
}

async function fetchChildrenOfItem(
  collectionId: number,
  parentId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem[]> {
  return (
    await createApiInstance(DataObjectApi).getAllDataObjects({
      parentId,
      collectionId,
    })
  ).map(item => mapToTreeviewItem(item, parentItem));
}
