import { DataObjectApi } from "@dlr-shepard/backend-client";
import {
  mapToTreeviewItem,
  mapToTreeviewItems,
  type CollectionRouteParams,
  type TreeviewItem,
} from "~/components/collection/collectionUtils";
import { useOpenedItems } from "./useOpenedItems";

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  const router = useRouter();
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const { openedTreeviewItems, addOpen } = useOpenedItems();

  async function fetchTreeviewItems(collectionId: number) {
    if (!collectionId) return;
    createApiInstance(DataObjectApi)
      .getAllDataObjects({ collectionId, parentId: -1 })
      .then(response => {
        // initial load of dataobject from a collection - no parents possible
        treeviewItems.value = mapToTreeviewItems(response, undefined);
      })
      .catch(error => {
        handleError(error, "getAllDataObjects");
      });
  }

  fetchTreeviewItems(routeParams.value.collectionId);

  async function expandAndLoadItem(itemId: number) {
    const itemWithPath = getItemIfLoaded(itemId);
    if (itemWithPath) {
      addOpen(itemWithPath.pathFromRoot);
      return;
    }

    const parentIds = await getPathToActiveItem(
      routeParams.value.collectionId,
      itemId,
    );
    for (const id of parentIds) {
      await loadChildrenOfItem(id);
    }
    addOpen(parentIds);
  }

  if (routeParams.value.dataObjectId) {
    expandAndLoadItem(routeParams.value.dataObjectId);
  }

  watch(routeParams, (_, oldParams) => {
    if (
      routeParams.value.collectionId &&
      oldParams.collectionId !== routeParams.value.collectionId
    ) {
      fetchTreeviewItems(routeParams.value.collectionId);
    }

    if (routeParams.value.dataObjectId) {
      expandAndLoadItem(routeParams.value.dataObjectId);
    }
  });

  /**
   * Searches for item in the already loaded tree
   *
   * @param itemId Id of the item to be found
   * @returns The item and the path from the root to the item (excluding the id of the item itself). Undefined if the item can't be found in the tree.
   */
  function getItemIfLoaded(
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
    const item = getItemIfLoaded(itemId)?.item;

    if (!item) return;
    if (!item.childrenIds?.length) {
      item.children = [];
    }
    if (item.children?.length) return;

    const children = mapToTreeviewItems(
      await createApiInstance(DataObjectApi).getAllDataObjects({
        parentId: item.id,
        collectionId: routeParams.value.collectionId,
      }),
      item,
    );

    item.children = children;
    item.childrenIds = children.map(c => c.id);
  }

  /**
   * @returns List of numbers representing the path to the item (excluding the id of the item itself)
   */
  async function getPathToActiveItem(
    collectionId: number,
    itemId: number,
  ): Promise<number[]> {
    async function getPathFromRootTo(currentPath: number[]): Promise<number[]> {
      const currentRoot = currentPath[0];
      if (currentRoot === undefined) {
        return [];
      }
      const currentRootItemWithPath = getItemIfLoaded(currentRoot);
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

  /**
   * Deletes a data object in the backend and updates the tree accordingly.
   *
   * @param itemId Id of the item to delete
   */
  async function deleteItem(itemId: number) {
    const deletionSuccessful = await createApiInstance(DataObjectApi)
      .deleteDataObject({
        collectionId: routeParams.value.collectionId,
        dataObjectId: itemId,
      })
      .then(() => true)
      .catch(error => {
        handleError(error, "deleteDataObject");
        return false;
      });
    if (!deletionSuccessful) return;

    removeItemFromTree(itemId);

    if (routeParams.value.dataObjectId === itemId) {
      router.push(collectionsPath + routeParams.value.collectionId);
    }
  }

  function removeItemFromTree(itemId: number) {
    const deletedItemWithPath = getItemIfLoaded(itemId);

    // This should not happen as the item needs to be loaded for the delete button to be rendered
    if (!deletedItemWithPath || !treeviewItems.value) return;

    const { item } = deletedItemWithPath;

    // Move orphaned children to top level
    if (item.children) {
      treeviewItems.value?.push(...item.children);
    }

    const parent = item.parent;

    if (parent === undefined) {
      // Remove item if it was on top level
      treeviewItems.value = treeviewItems.value?.filter(
        child => child.id !== item.id,
      );
    } else if (parent && parent.children && parent.childrenIds) {
      // Remove item from children of it's parent
      parent.children = parent.children.filter(child => child.id !== item.id);
      parent.childrenIds = parent.childrenIds.filter(id => id !== item.id);
      if (parent.children.length === 0 || parent.childrenIds.length === 0) {
        parent.children = undefined;
        parent.childrenIds = undefined;
      }
    }
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loadChildrenOfItem,
    deleteItem,
  };
};

export async function fetchTreeviewItem(
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
