<script setup lang="ts">
import {
  CollectionApi,
  DataObjectApi,
  type Collection,
  type DataObject,
} from "@dlr-shepard/backend-client";
import {
  isTreeViewItem,
  mapToTreeViewItems,
  type TreeViewItem,
} from "./collectionUtils";

interface CollectionSidebarProps {
  collectionId: number;
}

const collectionPathPattern = new RegExp("^/collections/\\d+$");
const dataObjectPathPattern = new RegExp(
  "^/collections/\\d+/dataobjects/(\\d+)$",
);

const props = defineProps<CollectionSidebarProps>();

const router = useRouter();
const route = useRoute();

const collectionId = props.collectionId;
const items = ref<TreeViewItem[] | undefined>(undefined);
const activatedIds = ref<number[]>([]);
const currentCollection = ref<Collection | undefined>();
const isCollectionHeaderFocused = ref<boolean>(false);

async function fetchCollection() {
  createApiInstance(CollectionApi)
    .getCollection({ collectionId })
    .then(response => {
      currentCollection.value = response;
    })
    .catch(error => {
      handleError(error, "getCollection");
    });
}

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
  if (Array.isArray(activeItems) && activeItems.length) {
    router.push(
      collectionsPath + collectionId + dataObjectsPathFragment + activeItems[0],
    );
    activatedIds.value = activeItems;
  }
}

watch(
  () => route.path,
  async newPath => {
    checkSidebarSelectionOnPathUpdate(newPath);
  },
);

onMounted(() => {
  checkSidebarSelectionOnPathUpdate(route.path);
});

/**
 * This function updates the sidebar selection to select either the collection header or one of the dataobject treeview items.
 * For this the passed `path` parameter is analyzed, to check if currently the collection-view or the dataobject-view is active.
 * @param path - path/ route that is currently shown
 */
function checkSidebarSelectionOnPathUpdate(path: string) {
  const isCollectionPath = collectionPathPattern.test(path);
  isCollectionHeaderFocused.value = isCollectionPath;
  if (isCollectionPath) {
    activatedIds.value = [];
    return;
  }

  const isDataObjectPath = dataObjectPathPattern.test(path);
  if (isDataObjectPath) {
    // extract dataobject Id from path
    const dataObjectId = parseInt(path.match(dataObjectPathPattern)![1]!);
    activatedIds.value = [dataObjectId];
  }
}

fetchRootDataObjectsOfCollection();
fetchCollection();
</script>

<template>
  <div class="bg-blue-grey-50 elevation-4" style="height: 100%">
    <div class="px-6 pt-6 pb-1 text-body-2 text-uppercase">Collection</div>
    <CollectionSideBarEntry
      :is-focused="isCollectionHeaderFocused"
      :to="collectionsPath + `${collectionId}`"
      height="40px"
      class="mb-4"
    >
      <div
        class="ml-1 text-h4"
        style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
      >
        {{ currentCollection?.name }}
      </div>
    </CollectionSideBarEntry>
    <v-divider thickness="1" />

    <div class="px-6 pt-6">
      <div class="text-body-2 text-uppercase">Contents</div>
    </div>
    <v-treeview
      v-if="!!items"
      class="bg-blue-grey-50"
      :items="items"
      item-value="id"
      :load-children="fetchChildren"
      activatable
      :activated="activatedIds"
      active-strategy="single-independent"
      color="blue-500"
      density="compact"
      mandatory
      collapse-icon="mdi-chevron-down"
      expand-icon="mdi-chevron-right"
      @update:activated="onActivated"
    />
    <LayoutComponentsCenteredLoadingSpinner v-else />
  </div>
</template>

<style>
.v-list-item--density-compact.v-list-item--one-line {
  min-height: unset;
}
</style>
