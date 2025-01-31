<script setup lang="ts">
import {
  ContainerFilterTypes,
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";

const router = useRouter();
const { queryParams } = useContainerListRouteParams();

const selectedFilter = ref<ContainerFilterType | null | undefined>(
  queryParams.value.selectedFilter,
);
const selectedFilterParam = computed(() => {
  if (selectedFilter.value === null) return undefined;
  return selectedFilter.value;
});

const filters = Object.values(ContainerFilterTypes).map(containerType => ({
  title: ContainerTypeName[containerType],
  value: containerType,
}));

function onSelectUpdate() {
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      selectedFilter: selectedFilterParam.value,
    },
  });
}
</script>

<template>
  <v-select
    v-model="selectedFilter"
    width="273px"
    clearable
    placeholder="Select container type"
    prepend-inner-icon="mdi-filter-variant"
    :list-props="{
      density: 'compact',
    }"
    :items="filters"
    item-color="primary"
    density="compact"
    variant="outlined"
    @update:model-value="onSelectUpdate"
  />
</template>

<style scoped lang="scss">
.v-select {
  :deep(.v-select__selection) {
    color: rgb(var(--v-theme-primary));
  }
}
</style>
