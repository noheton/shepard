<script setup lang="ts">
import {
  CollectionApi,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/backend-client";

const itemsPerPage = 5;
const headers = [
  { title: "ID", value: "id" },
  { title: "Name", value: "name" },
  {
    title: "Last changed",
    key: "lastChanged",
    value: "changedAt",
  },
  { title: "Created by", value: "createdBy" },
];
const search = "";
const serverItems = ref<Collection[]>([]);
const loading = ref<boolean>(true);
const totalItems = ref<number>(itemsPerPage + 1);

function fetchCollections({ page }: { page: number }) {
  createApiInstance(CollectionApi)
    .getAllCollections({
      size: itemsPerPage,
      page: page - 1,
    })
    .then(response => {
      serverItems.value = [];
      serverItems.value = response;

      loading.value = false;
      if (response.length >= itemsPerPage) {
        totalItems.value = page * itemsPerPage + 1;
      } else {
        totalItems.value = (page - 1) * itemsPerPage + response.length;
      }
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collections");
    });
}
</script>

<template>
  {{ serverItems.length }}
  <v-data-table-server
    :items-per-page="itemsPerPage"
    :headers="headers"
    :items="serverItems"
    :items-length="totalItems"
    :loading="loading"
    :search="search"
    @update:options="fetchCollections"
  />
</template>
