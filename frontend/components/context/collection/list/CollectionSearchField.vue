<script setup lang="ts">
import { useCollectionListQueryParams } from "./useCollectionListQueryParams";

const { queryParams } = useCollectionListQueryParams();
const router = useRouter();

defineProps<{
  searchResultHint: string | undefined;
}>();

function onSearch(searchText: string | undefined) {
  router.push({
    path: collectionsPath,
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
