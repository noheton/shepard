<script setup lang="ts">
import { useTreeviewItems } from "./useTreeviewItems";
import type { TreeviewItem } from "./treeviewItem";

const router = useRouter();
const { routeParams } = useCollectionRouteParams();
const {
  collection,
  isAllowedToEditCollection,
  isAllowedToEditPermissions,
  isOwner,
} = useFetchCollectionOfRouteParams(routeParams);
const {
  treeviewItems,
  openedTreeviewItems,
  loading,
  loadChildrenOfItem,
  refreshItems,
  collapseItem,
} = useTreeviewItems(routeParams);

// QW2: client-side text filter for data objects in the sidebar.
//
// Filtering applies to already-loaded nodes only. Children that have not been
// expanded/loaded will not appear in search results, because the treeview uses
// lazy-loading (children are fetched on first expand). This is intentional —
// no new server requests are issued while typing.
//
// Demo behaviour (LUMEN hotfire campaign):
//   - Filter "TR-00" matches TR-001 through TR-009 and their parent "Test Runs"
//   - Filter "TR-005" matches only TR-005, with "Test Runs" still visible as its
//     ancestor. This requires the "Test Runs" group to already be expanded so
//     that its children are loaded in memory.
const filterText = ref<string>("");

// Clear filter whenever the user navigates to a different collection.
watch(
  () => routeParams.value.collectionId,
  () => {
    filterText.value = "";
  },
);

/**
 * Recursively filters the treeview items tree.
 * A node is included if:
 *   (a) its title contains the needle (case-insensitive), OR
 *   (b) at least one of its (loaded) descendants matches.
 * When included due to (b) only, the node's children are replaced by the
 * filtered children list so only matching subtrees are shown.
 */
function filterItems(
  items: TreeviewItem[],
  needle: string,
): TreeviewItem[] {
  const lower = needle.toLowerCase();
  const result: TreeviewItem[] = [];
  for (const item of items) {
    const selfMatch = item.title.toLowerCase().includes(lower);
    const filteredChildren =
      item.children && item.children.length > 0
        ? filterItems(item.children, needle)
        : [];
    if (selfMatch || filteredChildren.length > 0) {
      result.push({
        ...item,
        // When there are matching descendants, surface only those; otherwise
        // preserve the original children array (which may be empty / undefined).
        children:
          filteredChildren.length > 0 ? filteredChildren : item.children,
      });
    }
  }
  return result;
}

/** The items actually passed to v-treeview (filtered when a needle is set). */
const filteredItems = computed<TreeviewItem[]>(() => {
  if (!treeviewItems.value) return [];
  const needle = filterText.value.trim();
  if (!needle) return treeviewItems.value;
  return filterItems(treeviewItems.value, needle);
});

/**
 * Collect IDs of all ancestors of matched nodes so the treeview can auto-open
 * them while the filter is active. Returns a Set of node IDs that should be
 * open in addition to whatever the user has already opened.
 */
function collectAncestorIds(
  items: TreeviewItem[],
  needle: string,
): Set<number> {
  const lower = needle.toLowerCase();
  const ids = new Set<number>();

  function walk(node: TreeviewItem): boolean {
    const selfMatch = node.title.toLowerCase().includes(lower);
    let childMatch = false;
    if (node.children) {
      for (const child of node.children) {
        if (walk(child)) childMatch = true;
      }
    }
    if (childMatch) {
      ids.add(node.id);
    }
    return selfMatch || childMatch;
  }

  for (const root of items) {
    walk(root);
  }
  return ids;
}

/**
 * The set of opened IDs to pass to v-treeview.
 * While filtering, we augment the user's opened set with ancestors of matches
 * so matched children are immediately visible without a manual expand.
 */
const effectiveOpenedItems = computed<number[]>(() => {
  const needle = filterText.value.trim();
  if (!needle || !treeviewItems.value) return openedTreeviewItems.value;
  const ancestors = collectAncestorIds(treeviewItems.value, needle);
  if (ancestors.size === 0) return openedTreeviewItems.value;
  const merged = new Set<number>([...openedTreeviewItems.value, ...ancestors]);
  return Array.from(merged);
});

async function onOpenClicked(expandGroup: {
  id: unknown;
  value: boolean;
  path: unknown[];
}) {
  if (!treeviewItems.value) return;
  // do not handle any specific case when closing group
  if (expandGroup.value === false) return;
  if (typeof expandGroup.id !== "number") return;

  loadChildrenOfItem(expandGroup.id);
}

function onActivated(activeItems: unknown) {
  if (Array.isArray(activeItems) && activeItems.length) {
    router.push(
      collectionsPath +
        routeParams.value.collectionId +
        dataObjectsPathFragment +
        activeItems[0],
    );
  }
}

function onDeleted(deletedItemId: number) {
  collapseItem(deletedItemId);
  refreshItems();
  if (routeParams.value.dataObjectId === deletedItemId) {
    router.push(collectionsPath + routeParams.value.collectionId);
  }
}

const createDataObjectDialogOpened = ref<boolean>(false);
</script>

<template>
  <div
    style="position: fixed; width: 100%; max-width: inherit"
    class="bg-treeview elevation-4 sidebar-container"
  >
    <CollectionSidebarHeader
      :is-focused="routeParams.dataObjectId === undefined"
      :collection="collection"
      :is-allowed-to-edit-collection="isAllowedToEditCollection"
      :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
      :is-owner="isOwner"
    />
    <v-divider opacity="100" class="text-low-emphasis mt-4" thickness="1px" />

    <div
      class="px-6 mt-6 mb-2 d-flex"
      style="justify-content: space-between; width: 100%; align-items: center"
    >
      <div
        class="text-textbody1 text-overline"
        style="
          text-overflow: ellipsis;
          overflow: hidden;
          white-space: nowrap;
          flex-shrink: 1;
          min-width: 0;
        "
      >
        Contents
      </div>
      <v-btn
        v-if="isAllowedToEditCollection"
        density="compact"
        variant="text"
        icon="mdi-plus-circle"
        color="primary"
        class="pa-0"
        @click="createDataObjectDialogOpened = true"
      />
    </div>

    <!-- QW2: filter input — visible only when tree has items -->
    <div v-if="!loading && treeviewItems && treeviewItems.length > 0" class="px-3 pb-2">
      <v-text-field
        v-model="filterText"
        placeholder="Filter…"
        density="compact"
        variant="outlined"
        clearable
        prepend-inner-icon="mdi-magnify"
        hide-details
      />
    </div>

    <div class="treeview-container">
      <div
        style="display: flex; flex-direction: column; align-items: flex-start"
      >
        <v-treeview
          v-if="!loading && !!treeviewItems && treeviewItems.length > 0"
          :opened="effectiveOpenedItems"
          :items="filteredItems"
          class="treeview"
          item-value="id"
          activatable
          :activated="[routeParams.dataObjectId]"
          active-strategy="single-independent"
          density="compact"
          active-class="treeview-active"
          mandatory
          collapse-icon="mdi-chevron-down"
          expand-icon="mdi-chevron-right"
          @update:opened="(val: number[]) => openedTreeviewItems = val"
          @update:activated="onActivated"
          @click:open="onOpenClicked"
        >
          <template #prepend="{ item }">
            <v-icon
              v-if="item.childrenIds?.length === 0"
              color="textbody1"
              icon="mdi-circle-small"
            />
          </template>

          <template #title="{ item }">
            <CollectionSidebarEntry
              :title="item.title"
              :is-focused="routeParams.dataObjectId === item.id"
              :to="
                collectionsPath +
                `${routeParams.collectionId}` +
                dataObjectsPathFragment +
                `${item.id}`
              "
            />
          </template>

          <template #append="{ item }">
            <CollectionSidebarItemContextMenu
              v-if="isAllowedToEditCollection"
              :collection-id="routeParams.collectionId"
              :data-object-id="item.id"
              :parent-id="item.parentId"
              :item-name="item.title"
              @data-object-created="refreshItems"
              @data-object-updated="refreshItems"
              @data-object-deleted="() => onDeleted(item.id)"
            />
          </template>
        </v-treeview>

        <CenteredLoadingSpinner v-if="loading || !treeviewItems" />

        <StartHereIntro
          v-if="treeviewItems && treeviewItems.length === 0"
          class="mb-8"
        />

        <div v-if="isAllowedToEditCollection" class="mt-0 mb-6 d-flex">
          <v-btn
            height="36"
            density="compact"
            variant="text"
            color="primary"
            class="ml-3"
            @click="createDataObjectDialogOpened = true"
          >
            <template #prepend>
              <v-icon size="24" icon="mdi-plus-circle" />
            </template>
            Add new data object
          </v-btn>
        </div>
      </div>
    </div>
  </div>
  <CreateDataObjectDialog
    v-if="createDataObjectDialogOpened"
    v-model:show-dialog="createDataObjectDialogOpened"
    :collection-id="routeParams.collectionId"
    @data-object-created="refreshItems"
  />
</template>

<style lang="scss" scoped>
.treeview {
  background-color: rgb(var(--v-theme-treeview));
}

.sidebar-container {
  display: flex;
  flex-direction: column;
  justify-content: start;
  // 64px is the top app bar height
  --sidebar-height: calc(100vh - 64px);
  height: var(--sidebar-height);
  max-height: var(--sidebar-height);
}

.treeview-container {
  overflow: auto;
  scrollbar-width: thin;

  height: 100%;

  :deep(.v-list) {
    min-width: 100%;
  }

  :deep(.treeview-active) {
    background-color: rgb(var(--v-theme-focus1));
    border-left: 5px solid rgb(var(--v-theme-primary)) !important;
  }

  /* Remove gray-dark overlay from treeview items */
  :deep(.v-list-item--active) > .v-list-item__overlay {
    visibility: hidden;
  }

  /* Align treeview expansion buttons with list decoration for vuetify 3.11  */
  :deep(.v-list-item-action--start) {
    margin-inline-end: 0;
    margin-inline-start: 0;
  }

  :deep(.v-list-item--density-compact.v-list-item--one-line) {
    min-height: unset;
  }

  :deep(.v-list-item) {
    padding-top: 0;
    padding-right: 24px;
    padding-bottom: 0;
    padding-left: 19px;
    // set border to all items, so that spacing is correct when focused
    border-left: 5px solid transparent;

    display: flex;
    width: inherit;
  }

  :deep(.v-list-item__content) {
    margin-right: auto;
    padding-right: 16px;
    min-width: fit-content;
    width: 100%;
  }

  :deep(.v-list-item__prepend) {
    margin-right: 16px;
    padding-right: 0px;
    width: 24px;

    .v-btn {
      width: 24px;
      height: 24px;
    }

    .v-treeview-item__level {
      display: none;
    }
  }

  :deep(.mdi-chevron-down) {
    color: rgb(var(--v-theme-medium-emphasis));
  }
  :deep(.mdi-chevron-right) {
    color: rgb(var(--v-theme-medium-emphasis));
  }
  :deep(.mdi-circle-small) {
    color: rgb(var(--v-theme-medium-emphasis));
  }

  :deep(.v-list-group--prepend) {
    --prepend-width: 11px;
  }
}
</style>
