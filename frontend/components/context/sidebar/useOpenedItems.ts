/**
 * V2-SWEEP Wave 1 (2026-06-10): opened-state is keyed on appId strings
 * (UUID v7) — the treeview's v2-native identity. appIds are stable across
 * refreshes and resets, so opened state survives a tree reload.
 */
export function useOpenedItems() {
  const openedTreeviewItems = ref<string[]>([]);

  function addOpen(toBeOpened: string[]) {
    openedTreeviewItems.value = Array.from(
      new Set([...openedTreeviewItems.value, ...toBeOpened]),
    );
  }

  function collapseItem(itemId: string) {
    openedTreeviewItems.value = openedTreeviewItems.value.filter(
      id => id !== itemId,
    );
  }

  return { openedTreeviewItems, addOpen, collapseItem };
}
