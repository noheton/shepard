<script setup lang="ts">
const router = useRouter();
const { routeParams } = useCollectionRouteParams();
const { collection } = useFetchCollectionOfRouteParams(routeParams);
const {
  treeviewItems,
  openedTreeviewItems,
  loading,
  loadChildrenOfItem,
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

// Todo: activeDataObjectId cannot be used because user can select
// edit from context menu without activating it
const showEditDataObjectDialog = ref(false);
const editDataObjectId = ref(0);
</script>

<template>
  <div class="bg-treeview elevation-4" style="height: 100%">
    <div class="px-6 pt-6 pb-1 text-body-2 text-uppercase">Collection</div>
    <CollectionSidebarHeader
      :is-focused="routeParams.dataObjectId === undefined"
      :to="collectionsPath + `${routeParams.collectionId}`"
      height="40px"
      class="mb-4"
    >
      <div
        class="ml-1 text-h4"
        style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
      >
        {{ collection?.name }}
      </div>
    </CollectionSidebarHeader>
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
        <CollectionSidebarTreeviewItemContextMenu>
          <CollectionSidebarDeleteDataObjectButton
            :delete-item="() => deleteItem(item.id)"
          />
          <v-btn
            class="text-textbody1 text-body-1"
            prepend-icon="mdi-pencil-outline"
            text="Edit"
            color="canvas"
            @click="
              editDataObjectId = item.id;
              showEditDataObjectDialog = true;
            "
          />
        </CollectionSidebarTreeviewItemContextMenu>
      </template>
    </v-treeview>
    <LayoutComponentsCenteredLoadingSpinner v-else />
    <DataObjectDataEditDialog
      v-if="showEditDataObjectDialog"
      v-model:show-dialog="showEditDataObjectDialog"
      :collection-id="collection?.id ?? 0"
      :data-object-id="editDataObjectId"
    />
  </div>
</template>

<style lang="css" scoped>
.treeview {
  background-color: rgb(var(--v-theme-treeview));
}
.bg-treeview {
  :deep(.treeview-active) {
    background-color: rgb(var(--v-theme-focus1));
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
  }
}
</style>
