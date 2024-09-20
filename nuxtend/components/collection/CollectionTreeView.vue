<script setup lang="ts">
import {
  getFakeDataObjectById,
  getFakeDataObjectsOfCollection,
  pause,
} from "./collectionFakeData";
import { isTreeViewItem, type TreeViewItem } from "./collectionUtils";

interface CollectionTreeViewProps {
  collectionId: number;
}

const props = defineProps<CollectionTreeViewProps>();
const emit = defineEmits<{
  dataObjectSelected: [dataObjectId: number];
}>();

const collectionId = props.collectionId;
const items = ref<TreeViewItem[]>([]);

async function loadDataObjectsOfCollection() {
  const dataObjects = await getFakeDataObjectsOfCollection(collectionId);
  items.value = dataObjects.map(dataObject => {
    return {
      id: dataObject.id ?? 1,
      title: dataObject.name ?? "",
      children: [] as TreeViewItem[],
      childrenIds: dataObject.childrenIds,
    };
  });
}

async function loadChildren(item: unknown) {
  if (!isTreeViewItem(item)) return;
  // Do not load if no childrenIds or children already loaded
  if (!item.childrenIds?.length || item.children?.length) return;

  await pause(1500);

  if (Array.isArray(item?.childrenIds)) {
    const dataObjectsToAdd = await Promise.all(
      item.childrenIds.map(async (childId: number) => {
        return getFakeDataObjectById(collectionId, childId);
      }),
    );

    const childsToAdd = dataObjectsToAdd.map(dataObject => {
      return {
        id: dataObject.id,
        title: dataObject.name,
        childrenIds: dataObject.childrenIds,
        children: dataObject.childrenIds?.length ? [] : undefined,
      } as TreeViewItem;
    });
    item.children.push(...childsToAdd);
  }
}

function onActivated(activeItems: unknown) {
  if (Array.isArray(activeItems)) {
    if (!activeItems.length) return;
    emit("dataObjectSelected", activeItems[0]);
  }
}

loadDataObjectsOfCollection();
</script>

<template>
  <div>
    <v-treeview
      :items="items"
      item-title="title"
      item-value="id"
      :load-children="loadChildren"
      activatable
      active-strategy="single-independent"
      color="warning"
      density="compact"
      mandatory
      @update:activated="onActivated"
    />
  </div>
</template>
