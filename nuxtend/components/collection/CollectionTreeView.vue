<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";
import {
  isTreeViewItem,
  mapToTreeViewItems,
  type TreeViewItem,
} from "./collectionUtils";

interface CollectionTreeViewProps {
  collectionId: number;
}

const props = defineProps<CollectionTreeViewProps>();
const emit = defineEmits<{
  dataObjectSelected: [dataObjectId: number];
}>();

const collectionId = props.collectionId;
const items = ref<TreeViewItem[]>([]);

async function fetchRootDataObjectsOfCollection() {
  createApiInstance(DataObjectApi)
    .getAllDataObjects({ collectionId, parentId: -1 })
    .then(response => {
      items.value = mapToTreeViewItems(response);
    })
    .catch(error => {
      handleError(error, "getAllDataObjects");
    });
}

async function fetchChildren(item: unknown) {
  if (!isTreeViewItem(item)) return;
  // Do not load if no childrenIds or children already loaded
  if (!item.childrenIds?.length || item.children?.length) return;

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
  if (Array.isArray(activeItems)) {
    if (!activeItems.length) return;
    emit("dataObjectSelected", activeItems[0]);
  }
}

fetchRootDataObjectsOfCollection();
</script>

<template>
  <div>
    <v-treeview
      :items="items"
      item-title="title"
      item-value="id"
      :load-children="fetchChildren"
      activatable
      active-strategy="single-independent"
      color="warning"
      density="compact"
      mandatory
      @update:activated="onActivated"
    />
  </div>
</template>
