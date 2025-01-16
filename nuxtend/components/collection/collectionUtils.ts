import type { RouteParamsGeneric } from "#vue-router";
import type { DataObject } from "@dlr-shepard/backend-client";

export interface TreeViewItem {
  id: number;
  title: string;
  children: TreeViewItem[] | undefined;
  childrenIds: number[] | undefined;
}

export function isTreeViewItem(
  treeViewItem: unknown,
): treeViewItem is TreeViewItem {
  const item = treeViewItem as TreeViewItem;
  return item.id !== undefined && item.title !== undefined;
}

export function mapToTreeViewItem(dataObject: DataObject): TreeViewItem {
  return {
    id: dataObject.id ?? 0,
    title: dataObject.name ?? "",
    childrenIds: dataObject.childrenIds,
    children: dataObject.childrenIds?.length
      ? ([] as TreeViewItem[])
      : undefined,
  };
}

export function mapToTreeViewItems(dataObjects: DataObject[]): TreeViewItem[] {
  return dataObjects.map(mapToTreeViewItem);
}

export interface CollectionRouteParams {
  collectionId?: number;
  dataObjectId?: number;
}

/**
 * A helper function to parse the router parameter to create an instance of `CollectionRouteParams`.
 * @param routeParams - RouteParamsGeneric
 * @returns Returns `undefined` if the collectionId was not present in the route params, else returns an instance of `CollectionRouteParams`
 */
export function getCollectionRouterParamsFromRoute(
  routeParams: RouteParamsGeneric,
): CollectionRouteParams {
  let collectionId: number | null = null;
  let dataObjectId: number | undefined = undefined;

  if (
    routeParams.collectionId &&
    typeof routeParams.collectionId === "string"
  ) {
    collectionId = parseInt(routeParams.collectionId);
  } else {
    return {};
  }

  if (
    routeParams.dataObjectId &&
    typeof routeParams.dataObjectId === "string"
  ) {
    dataObjectId = parseInt(routeParams.dataObjectId);
  }

  return { collectionId: collectionId, dataObjectId: dataObjectId };
}
