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
