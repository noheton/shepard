import type { RouteParamsGeneric } from "#vue-router";
import type { DataObject } from "@dlr-shepard/backend-client";

export interface TreeviewItem {
  id: number;
  title: string;
  children: TreeviewItem[] | undefined;
  childrenIds: number[] | undefined;
  parent: TreeviewItem | undefined;
  parentId: number | undefined;
}

export function isTreeviewItem(
  treeviewItem: unknown,
): treeviewItem is TreeviewItem {
  const item = treeviewItem as TreeviewItem;
  return item.id !== undefined && item.title !== undefined;
}

export function mapToTreeviewItem(
  dataObject: DataObject,
  parentItem: TreeviewItem | undefined,
): TreeviewItem {
  return {
    id: dataObject.id ?? 0,
    title: dataObject.name ?? "",
    childrenIds: dataObject.childrenIds,
    children: dataObject.childrenIds?.length
      ? ([] as TreeviewItem[])
      : undefined,
    parent: parentItem,
    parentId: dataObject.parentId ?? undefined,
  };
}

/**
 * Map multiple child dataobjects to Treeview items with a single parent
 */
export function mapToTreeviewItems(
  dataObjects: DataObject[],
  parentItem: TreeviewItem | undefined,
): TreeviewItem[] {
  return dataObjects.map(dataObject =>
    mapToTreeviewItem(dataObject, parentItem),
  );
}

export interface CollectionRouteParams {
  collectionId: number;
  dataObjectId?: number;
}

export const isCollectionRouteParams = (
  routeParams: Partial<CollectionRouteParams>,
): routeParams is CollectionRouteParams => {
  if (!routeParams.collectionId) return false;
  return true;
};

/**
 * A helper function to parse the router parameter to create an instance of `CollectionRouteParams`.
 * @param routeParams - RouteParamsGeneric
 * @returns Returns `undefined` if the collectionId was not present in the route params, else returns an instance of `CollectionRouteParams`
 */
export function getCollectionRouterParamsFromRoute(
  routeParams: RouteParamsGeneric,
): Partial<CollectionRouteParams> {
  return {
    collectionId: parseCollectionId(routeParams),
    dataObjectId: parseDataObjectId(routeParams),
  };
}

function parseCollectionId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.collectionId &&
    typeof routeParams.collectionId === "string" &&
    !Number.isNaN(routeParams.collectionId)
  ) {
    return parseInt(routeParams.collectionId);
  }
  return undefined;
}

function parseDataObjectId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.dataObjectId &&
    typeof routeParams.dataObjectId === "string"
  ) {
    return parseInt(routeParams.dataObjectId);
  }
  return undefined;
}
