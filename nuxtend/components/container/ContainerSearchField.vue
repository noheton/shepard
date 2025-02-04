<script setup lang="ts">
const { queryParams } = useContainerListRouteParams();
const router = useRouter();

const searchText = ref<string | null | undefined>(queryParams.value.searchText);
const searchTextParam = computed(() => {
  if (searchText.value === null) return undefined;
  return searchText.value;
});

function onSearch() {
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      searchText: searchTextParam.value,
    },
  });
}
</script>

<template>
  <v-text-field
    v-model="searchText"
    :clearable="true"
    density="compact"
    color="primary"
    placeholder="Search"
    variant="outlined"
    :hide-details="true"
    width="599px"
    style="box-shadow: 0px 12px 30px 0px rgba(16, 24, 40, 0.05)"
    @keydown.enter="onSearch"
    @click:clear="onSearch"
  >
    <template #append-inner>
      <v-btn variant="flat" color="primary" text="Search" @click="onSearch" />
    </template>
  </v-text-field>
</template>

<style scoped lang="scss">
.v-input--density-compact {
  --v-input-control-height: 42px;
}

:deep(.v-field--appended) {
  padding-inline-end: 3px;
}
</style>
