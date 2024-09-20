export interface TreeViewItem {
  id: number;
  title: string;
  children: TreeViewItem[];
  childrenIds: number[] | undefined;
}

export function isTreeViewItem(
  treeViewItem: unknown,
): treeViewItem is TreeViewItem {
  const item = treeViewItem as TreeViewItem;
  return (
    item.id !== undefined &&
    item.title !== undefined &&
    item.children !== undefined
  );
}
