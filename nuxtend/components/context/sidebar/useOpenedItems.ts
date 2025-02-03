export function useOpenedItems() {
  const openedTreeviewItems = ref<number[]>([]);

  function addOpen(toBeOpened: number[]) {
    openedTreeviewItems.value = uniqueNumbersOf([
      ...openedTreeviewItems.value,
      ...toBeOpened,
    ]);
  }

  function close(itemId: number) {
    openedTreeviewItems.value = openedTreeviewItems.value.filter(
      id => id !== itemId,
    );
  }

  return { openedTreeviewItems, addOpen, close };
}
