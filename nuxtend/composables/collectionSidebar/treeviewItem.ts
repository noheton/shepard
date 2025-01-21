import type { DataObject } from "@dlr-shepard/backend-client";

export interface TreeviewItem {
  id: number;
  title: string;
  children: TreeviewItem[] | undefined;
  childrenIds: number[] | undefined;
  parent: TreeviewItem | undefined;
  parentId: number | undefined;
}

export function mapToTreeviewItem(
  dataObject: DataObject,
  parentItem: TreeviewItem | undefined,
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
