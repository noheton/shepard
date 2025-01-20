export function useOpenedItems() {
  const openedTreeviewItems = ref<number[]>([]);

  function addOpen(toBeOpened: number[]) {
    const diffIdList = toBeOpened.filter(
      pId => !openedTreeviewItems.value.some(oId => oId === pId),
    );
    openedTreeviewItems.value.push(...diffIdList);
  }

  return { openedTreeviewItems, addOpen };
}
