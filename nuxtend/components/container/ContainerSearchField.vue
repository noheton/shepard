<script setup lang="ts">
const { queryParams } = useContainerListRouteParams();
const router = useRouter();

const { searchResultHint } = defineProps<{
  searchResultHint: string | undefined;
}>();

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
    :hint="searchResultHint"
    :persistent-hint="true"
    :clearable="true"
    density="compact"
    color="primary"
    placeholder="Search"
    variant="outlined"
    hide-details="auto"
    width="599px"
    @keydown.enter="onSearch"
    @click:clear="onSearch"
  >
    <template #append-inner>
      <v-btn variant="flat" color="primary" text="Search" @click="onSearch" />
    </template>
    <template #prepend-inner>
      <v-icon icon="mdi-magnify" size="x-small" />
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

:deep(.v-input__control) {
  box-shadow: 0px 12px 30px 0px rgba(16, 24, 40, 0.05);
}

:deep(.v-input__details) {
  padding-left: 0;
}

:deep(.v-messages__message) {
  font-size: 14px;
  font-style: normal;
  font-weight: 500;
  line-height: 22px;
  color: rgb(var(--v-theme-low-emphasis));
}
</style>
