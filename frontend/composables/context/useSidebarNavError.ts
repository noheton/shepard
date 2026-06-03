/**
 * UX-WALK-2026-05-29-06: shared singleton that tracks whether the sidebar
 * treeview navigation fetch failed.  Both the `CollectionSidebar` (which sets
 * the error) and the DataObject detail page (which reads it to surface the
 * dismissable warning banner) reference the same module-level ref.
 *
 * Reset is keyed to the collection id so that navigating to a different
 * collection clears the stale error state.
 */
const _treeviewError = ref(false);
const _treeviewErrorCollectionId = ref<number | null>(null);

export function useSidebarNavError() {
  function setTreeviewError(collectionId: number) {
    _treeviewError.value = true;
    _treeviewErrorCollectionId.value = collectionId;
  }

  function clearTreeviewError(collectionId: number) {
    if (_treeviewErrorCollectionId.value === collectionId) {
      _treeviewError.value = false;
      _treeviewErrorCollectionId.value = null;
    }
  }

  /** True when the last treeview fetch for the tracked collection failed. */
  const treeviewError = computed(() => _treeviewError.value);

  return { treeviewError, setTreeviewError, clearTreeviewError };
}
