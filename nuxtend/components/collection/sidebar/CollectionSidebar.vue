<script setup lang="ts">
const router = useRouter();
const { routeParams } = useCollectionRouteParams();
const { collection, isAllowedToEditCollection, isAllowedToEditPermissions } =
  useFetchCollectionOfRouteParams(routeParams);
const {
  treeviewItems,
  openedTreeviewItems,
  loading,
  loadChildrenOfItem,
  refreshItems,
  deleteItem,
} = useTreeviewItems(routeParams);

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
</script>

<template>
  <div class="bg-treeview elevation-4" style="height: 100%">
    <div class="px-6 pt-6 pb-1 text-body-2 text-uppercase">Collection</div>
    <CollectionSidebarHeader
      :is-focused="routeParams.dataObjectId === undefined"
      height="40px"
      :collection="collection"
      :is-allowed-to-edit-collection="isAllowedToEditCollection"
      :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
    />
    <v-divider thickness="1" />

    <div class="px-6 pt-6">
      <div class="text-body-2 text-uppercase">Contents</div>
    </div>
    <v-treeview
      v-if="!loading && !!treeviewItems"
      v-model:opened="openedTreeviewItems"
      :items="treeviewItems"
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
      @update:activated="onActivated"
      @click:open="onOpenClicked"
    >
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
          :delete-item="() => deleteItem(item.id)"
          :item-name="item.title"
          class="sidebar-item-context-menu"
          @data-object-updated="refreshItems"
        />
      </template>
    </v-treeview>
    <LayoutComponentsCenteredLoadingSpinner v-else />
  </div>
</template>

<style lang="scss" scoped>
.treeview {
  background-color: rgb(var(--v-theme-treeview));
}
.bg-treeview {
  :deep(.treeview-active) {
    background-color: rgb(var(--v-theme-focus1));
    border-left: 5px solid rgb(var(--v-theme-primary)) !important;
  }

  /* Remove gray-dark overlay from treeview items */
  :deep(.v-list-item--active) > .v-list-item__overlay {
    visibility: hidden;
  }

  :deep(.v-list-item--density-compact.v-list-item--one-line) {
    min-height: unset;
  }

  :deep(.v-list-item) {
    padding-top: 0;
    padding-bottom: 0;
    border-left: 5px solid transparent;
  }

  // TODO: Make this still visible when the context menu is opened
  :deep(.sidebar-item-context-menu) {
    visibility: hidden;
  }

  :deep(.v-list-item:hover) .sidebar-item-context-menu {
    visibility: visible;
  }
}
</style>
