import type { DataObject } from "@dlr-shepard/backend-client";

export interface TreeviewItem {
  id: number;
  title: string;
  children: TreeviewItem[] | undefined;
  childrenIds: number[] | undefined;
  parent: TreeviewItem | undefined;
  parentId: number | undefined;
}

/**
 * @param dataObject The data object to be mapped
 * @param parentItem If present and the id matches dataObject.parentId the item will be attached as parent
 */
export function mapToTreeviewItem(
  dataObject: DataObject,
  parentItem?: TreeviewItem,
): TreeviewItem {
  return {
    id: dataObject.id,
    title: dataObject.name,
    childrenIds: dataObject.childrenIds,
    children: dataObject.childrenIds?.length ? [] : undefined,
    parent:
      dataObject.parentId && parentItem?.id === dataObject.parentId
        ? parentItem
        : undefined,
    parentId: dataObject.parentId ?? undefined,
  };
}
