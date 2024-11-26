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

function fetchCollection(collectionId: number) {
  createApiInstance(CollectionApi)
    .getCollection({ collectionId })
    .then(response => {
      collection.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

fetchCollection(collectionId);

const dataObject = ref<DataObject | undefined>(undefined);

async function fetchDataObjectDetails() {
  if (dataObjectId)
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

fetchDataObjectDetails();
</script>

<template>
  <div v-if="!!collection && !!dataObject">
    <LayoutComponentsShepardBreadcrumbs
      :items="[
        {
          title: 'Collections',
          href: collectionsPath,
        },
        {
          title: `Collection '${collection?.name ?? 'Not Found'}'`,
          href: collectionsPath + collectionId,
        },
        {
          title: dataObject.name ?? 'No Data Object Name',
          href:
            collectionsPath +
            collectionId +
            dataObjectsPathFragment +
            dataObjectId,
        },
      ]"
    />
    <DataObjectDetailView
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
    />
  </div>
  <v-progress-circular v-else indeterminate />
</template>
