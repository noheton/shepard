<script setup lang="ts">
import {
  CollectionApi,
  DataObjectApi,
  type Collection,
  type DataObject,
} from "@dlr-shepard/backend-client";
import {
  getCollectionRouterParamsFromRoute,
  isTreeViewItem,
  mapToTreeViewItems,
  type CollectionRouteParams,
  type TreeViewItem,
} from "./collectionUtils";

interface CollectionSidebarProps {
  collectionRouteParams: CollectionRouteParams;
}

const props = defineProps<CollectionSidebarProps>();

const router = useRouter();
const route = useRoute();

const collectionId = props.collectionRouteParams.collectionId;
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
  () => route.params,
  () => {
    const routeParams = getCollectionRouterParamsFromRoute(route.params)!;
    switchFocusOnParams(routeParams);
  },
);

onMounted(() => {
  switchFocusOnParams(props.collectionRouteParams);
});

function switchFocusOnParams(routeParams: CollectionRouteParams) {
  if (routeParams.dataObjectId) {
    isCollectionHeaderFocused.value = false;
    activatedIds.value = [routeParams.dataObjectId];
  } else if (routeParams.collectionId) {
    isCollectionHeaderFocused.value = true;
    activatedIds.value = [];
  }
}

fetchRootDataObjectsOfCollection();
fetchCollection();
</script>

<template>
  <div class="bg-treeview elevation-4" style="height: 100%">
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
      class="bg-treeview"
      :items="items"
      item-value="id"
      :load-children="fetchChildren"
      activatable
      :activated="activatedIds"
      active-strategy="single-independent"
      color="primary"
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
