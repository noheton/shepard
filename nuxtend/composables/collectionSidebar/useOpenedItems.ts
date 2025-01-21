export function useOpenedItems() {
  const openedTreeviewItems = ref<number[]>([]);

  function addOpen(toBeOpened: number[]) {
    openedTreeviewItems.value = uniqueNumbersOf([
      ...openedTreeviewItems.value,
      ...toBeOpened,
    ]);
  }

  return { openedTreeviewItems, addOpen };
}

function uniqueNumbersOf(array: number[]): number[] {
  return [...new Set(array)];
}
