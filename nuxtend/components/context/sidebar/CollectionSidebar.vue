<script setup lang="ts">
import { useTreeviewItems } from "./useTreeviewItems";

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

const createDataObjectDialogOpened = ref<boolean>(false);
</script>

<template>
  <div class="bg-treeview elevation-4" style="height: 100%">
    <div class="px-6 pt-6 pb-1 text-textbody1 text-overline">Collection</div>
    <CollectionSidebarHeader
      :is-focused="routeParams.dataObjectId === undefined"
      height="40px"
      :collection="collection"
      :is-allowed-to-edit-collection="isAllowedToEditCollection"
      :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
    />
    <v-divider thickness="1" />

    <div class="px-6 pt-6 d-flex" :style="{ alignItems: 'center' }">
      <div class="text-textbody1 text-overline">Contents</div>
      <v-spacer />
      <div>
        <v-btn
          density="compact"
          variant="text"
          icon="mdi-plus-circle"
          color="primary"
          class="pa-0"
          size="small"
          @click="createDataObjectDialogOpened = true"
        />
      </div>
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
          :item-name="item.title"
          @data-object-created="refreshItems"
          @data-object-updated="refreshItems"
          @data-object-deleted="
            refreshItems();
            if (routeParams.dataObjectId === item.id) {
              router.push(collectionsPath + routeParams.collectionId);
            }
          "
        />
      </template>
    </v-treeview>
    <CenteredLoadingSpinner v-else />
    <div class="px-6 pt-6 d-flex" :style="{ alignItems: 'center' }">
      <v-btn
        density="compact"
        variant="text"
        color="primary"
        class="pa-0"
        @click="createDataObjectDialogOpened = true"
      >
        <template #prepend><v-icon icon="mdi-plus-circle" /></template>
        Add new data object
      </v-btn>
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
}
</style>
