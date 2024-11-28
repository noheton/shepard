<script setup lang="ts">
import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import { collectionsPath } from "../../../utils/constants";

definePageMeta({ layout: "collection" });

const route = useRoute();
const collectionId = parseInt(route.params.collectionId as string);

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
</script>

<template>
  <v-container fluid class="pa-0 fill-height align-start">
    <v-row v-if="!!collection" no-gutters>
      <v-col cols="12">
        <LayoutComponentsShepardBreadcrumbs
          :items="[
            {
              title: 'Collections',
              to: collectionsPath,
            },
            {
              title: `Collection '${collection.name}'`,
              to: collectionsPath + collectionId,
            },
          ]"
        />
      </v-col>
      <v-col cols="12">
        <EntityTitle :entity="collection" id-label="Collection ID" />
      </v-col>
    </v-row>
    <LayoutComponentsCenteredLoadingSpinner v-else />
  </v-container>
</template>
