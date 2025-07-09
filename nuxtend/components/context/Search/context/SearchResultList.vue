<script lang="ts" setup>
export type SearchResult = { id: number; name: string; url: string };

defineProps<{ searchResults: SearchResult[]; loading: boolean }>();

const headers = [
  {
    title: "ID",
    key: "id",
  },
  { title: "Name", key: "name" },
];

const itemsPerPage = 10;
</script>

<template>
  <NavigationDataTable
    :headers="headers"
    :hide-default-footer="true"
    :items-for-pagination="searchResults"
    :items-per-page="itemsPerPage"
    :loading="loading"
    :new-tab="true"
  />
</template>

<style lang="scss" scoped></style>
