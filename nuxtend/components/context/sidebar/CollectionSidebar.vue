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
  collapseItem,
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
  <div class="bg-treeview elevation-4 sidebar-container">
    <div
      class="ml-6 text-textbody1 text-overline"
      style="margin-top: 40px; margin-bottom: 4px"
    >
      Collection
    </div>
    <CollectionSidebarHeader
      :is-focused="routeParams.dataObjectId === undefined"
      :collection="collection"
      :is-allowed-to-edit-collection="isAllowedToEditCollection"
      :is-allowed-to-edit-permissions="isAllowedToEditPermissions"
      style="max-height: 40px; min-height: 40px"
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

    <div class="treeview-container">
      <div
        style="display: flex; flex-direction: column; align-items: flex-start"
      >
        <v-treeview
          v-if="!loading && !!treeviewItems && treeviewItems.length > 0"
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
          <template #prepend="{ item }">
            <v-icon v-if="item.childrenIds?.length === 0">
              mdi-circle-small
            </v-icon>
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
  }

  :deep(.v-list-item__prepend) {
    margin-right: 16px;
    padding-right: 0px;
    width: 24px;

    .v-btn {
      width: 24px;
      height: 24px;
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
