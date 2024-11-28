<script setup lang="ts">
import {
  type Collection,
  CollectionApi,
  type DataObject,
  DataObjectApi,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import {
  collectionsPath,
  dataObjectsPathFragment,
} from "../../../../utils/constants";

definePageMeta({ layout: "collection" });

const route = useRoute();
const collectionId = parseInt(route.params.collectionId as string);
const dataObjectId = parseInt(route.params.dataObjectId as string);

const collection = ref<Collection | undefined>(undefined);
const dataObject = ref<DataObject | undefined>(undefined);

function fetchCollection() {
  createApiInstance(CollectionApi)
    .getCollection({ collectionId })
    .then(response => {
      collection.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

async function fetchDataObject() {
  createApiInstance(DataObjectApi)
    .getDataObject({
      collectionId: collectionId,
      dataObjectId: dataObjectId,
    })
    .then(response => {
      dataObject.value = response;
    })
    .catch(error => {
      handleError(error, "getDataObject");
    });
}

fetchCollection();
fetchDataObject();
</script>

<template>
  <div v-if="!!collection && !!dataObject">
    <LayoutComponentsShepardBreadcrumbs
      :items="[
        {
          title: 'Collections',
          to: collectionsPath,
        },
        {
          title: `Collection '${collection.name}'`,
          to: collectionsPath + collection.id,
        },
        {
          title: dataObject.name,
          to:
            collectionsPath +
            collectionId +
            dataObjectsPathFragment +
            dataObjectId,
        },
      ]"
    />
    <DataObjectDetailView
      :collection-id="collection.id"
      :data-object="dataObject"
    />
  </div>
  <LayoutComponentsCenteredLoadingSpinner v-else />
</template>
