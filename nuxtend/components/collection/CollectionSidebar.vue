<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";
import { useCollection } from "~/composables/collection";
import { useDataObjectListByCollection } from "~/composables/dataObjectList";
import {
  getCollectionRouterParamsFromRoute,
  isTreeViewItem,
  mapToTreeViewItems,
  type CollectionRouteParams,
} from "./collectionUtils";

interface CollectionSidebarProps {
  collectionRouteParams: CollectionRouteParams;
}

const props = defineProps<CollectionSidebarProps>();

const router = useRouter();
const route = useRoute();

const collectionId = props.collectionRouteParams.collectionId;
const { collection: currentCollection } = useCollection(collectionId);
const { dataObjectsList: items } = useDataObjectListByCollection(
  collectionId,
  -1,
);
// according to documentation (https://vuetifyjs.com/en/api/v-treeview/#props-activated) the activated treeview items are a list of ids
// in our case we can assume that this array always only contains one id
const activatedIds = ref<number[]>([]);
const isCollectionHeaderFocused = ref<boolean>(false);

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
        {{ currentCollection?.name }}
      </div>
    </CollectionSideBarHeader>
    <v-divider thickness="1" />

    <div class="px-6 pt-6">
      <div class="text-body-2 text-uppercase">Contents</div>
    </div>
    <v-treeview
      v-if="!!items"
      class="treeview"
      :items="items"
      item-value="id"
      :item-props="true"
      :load-children="fetchChildren"
      activatable
      :activated="activatedIds"
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
          :is-focused="activatedIds.includes(item.id)"
          :to="
            collectionsPath +
            `${collectionId}` +
            dataObjectsPathFragment +
            `${item.id}`
          "
        />
      </template>
    </v-treeview>
    <LayoutComponentsCenteredLoadingSpinner v-else />
  </div>
</template>

<style lang="css">
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
