<script setup lang="ts">
import { useContainerListQueryParams } from "./useContainerListQueryParams";

const { queryParams } = useContainerListQueryParams();
const router = useRouter();

defineProps<{
  searchResultHint: string | undefined;
}>();

function onSearch(searchText: string | undefined) {
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      searchText,
    },
  });
}
</script>

<template>
  <SearchField
    :initial-search-text="queryParams.searchText ?? ''"
    :search-result-hint="searchResultHint"
    placeholder="Search by ID, Name or Created by"
    @search-term-updated="onSearch"
  />
</template>
