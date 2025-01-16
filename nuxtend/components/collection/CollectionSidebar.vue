<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";
import { useCollectionSidebarFocus } from "~/composables/collectionSidebarFocus";
import { useCollectionWithChildren } from "~/composables/collectionWithChildren";
import CollectionSidebarTreeviewItemContextMenu from "./CollectionSidebarTreeviewItemContextMenu.vue";
import {
  isTreeViewItem,
  mapToTreeViewItems,
  type CollectionRouteParams,
} from "./collectionUtils";

const router = useRouter();

const onRouteChange = (newParams: CollectionRouteParams) => {
  if (
    newParams.collectionId &&
    collection.value?.id !== newParams.collectionId
  ) {
    refetchCollectionAndChildren(newParams.collectionId);
  }
};
const { routeParams, activeDataObjectId, isCollectionHeaderFocused } =
  useCollectionSidebarFocus(onRouteChange);
const { collectionId } = routeParams.value;

const { collection, children, refetchCollectionAndChildren } =
  useCollectionWithChildren(collectionId);

async function fetchChildren(item: unknown) {
  if (!isTreeViewItem(item)) return;
  // Do not load if no childrenIds or children already loaded
  if (!item.childrenIds?.length || item.children?.length) return;
  if (!collectionId) return;

  if (Array.isArray(item?.childrenIds)) {
    const dataObjectsToAdd = await Promise.all(
      item.childrenIds.map(async (childId: number) => {
        return fetchDataObject(collectionId, childId);
      }),
    );

    item.children?.push(...mapToTreeViewItems(dataObjectsToAdd));
  }
}

function fetchDataObject(
  collectionId: number,
  dataObjectId: number,
): Promise<DataObject> {
  return createApiInstance(DataObjectApi).getDataObject({
    collectionId,
    dataObjectId,
  });
}

function onActivated(activeItems: unknown) {
  if (collectionId && Array.isArray(activeItems) && activeItems.length) {
    router.push(
      collectionsPath + collectionId + dataObjectsPathFragment + activeItems[0],
    );
  }
}

async function deleteDataObject(dataObjectId: number) {
  if (!collectionId) return;
  const deletionSuccessful = await createApiInstance(DataObjectApi)
    .deleteDataObject({
      collectionId: collectionId,
      dataObjectId: dataObjectId,
    })
    .then(() => true)
    .catch(error => {
      handleError(error, "deleteDataObject");
      return false;
    });
  if (!deletionSuccessful) return;
  // TODO: Refetch/update without losing opened elements
  refetchCollectionAndChildren(collectionId);
  if (activeDataObjectId.value === dataObjectId) {
    router.push(collectionsPath + collectionId);
  }
}
</script>

<template>
  <div class="bg-treeview elevation-4" style="height: 100%">
    <div class="px-6 pt-6 pb-1 text-body-2 text-uppercase">Collection</div>
    <CollectionSideBarHeader
      :is-focused="isCollectionHeaderFocused"
      :to="collectionsPath + `${collectionId}`"
      height="40px"
      class="mb-4"
    >
      <div
        class="ml-1 text-h4"
        style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
      >
        {{ collection?.name }}
      </div>
    </CollectionSideBarHeader>
    <v-divider thickness="1" />

    <div class="px-6 pt-6">
      <div class="text-body-2 text-uppercase">Contents</div>
    </div>
    <v-treeview
      v-if="!!children"
      class="treeview"
      :items="children"
      item-value="id"
      :item-props="true"
      :load-children="fetchChildren"
      activatable
      :activated="[activeDataObjectId]"
      active-strategy="single-independent"
      density="compact"
      active-class="treeview-active"
      mandatory
      collapse-icon="mdi-chevron-down"
      expand-icon="mdi-chevron-right"
      @update:activated="onActivated"
    >
      <template #title="{ item }">
        <CollectionSideBarEntry
          :title="item.title"
          :is-focused="activeDataObjectId === item.id"
          :to="
            collectionsPath +
            `${collectionId}` +
            dataObjectsPathFragment +
            `${item.id}`
          "
        />
      </template>
      <template #append="{ item }">
        <CollectionSidebarTreeviewItemContextMenu
          :delete-item="() => deleteDataObject(item.id)"
        />
      </template>
    </v-treeview>
    <LayoutComponentsCenteredLoadingSpinner v-else />
  </div>
</template>

<style lang="css" scoped>
.treeview {
  background-color: rgb(var(--v-theme-treeview));
}

.treeview-active {
  background-color: rgb(var(--v-theme-focus1));
}

/* Remove gray-dark overlay from treeview items */
.v-list-item--active > .v-list-item__overlay {
  visibility: hidden;
}

.v-list-item--density-compact.v-list-item--one-line {
  min-height: unset;
}

.v-list-item {
  padding-top: 0;
  padding-bottom: 0;
}
</style>
