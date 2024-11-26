<script setup lang="ts">
import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import { collectionsPath } from "../../../utils/constants";

definePageMeta({ layout: "collection" });

const route = useRoute();
const collectionId = parseInt(route.params.collectionId as string);

const collection = ref<Collection>();

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
    <v-row no-gutters class="fill-height">
      <v-col cols="12">
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
          ]"
        />
      </v-col>
    </v-row>
    <v-row no-gutters class="fill-height">
      <v-col cols="12">
        <div>No Content</div>
      </v-col>
    </v-row>
  </v-container>
</template>
