<script setup lang="ts">
import Select from "../common/Select.vue";
import {
  ContainerFilterTypes,
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";
import { useContainerListQueryParams } from "./useContainerListQueryParams";

const router = useRouter();
const { queryParams } = useContainerListQueryParams();

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
  <Select
    v-model="selectedFilter"
    width="273px"
    clearable
    placeholder="Select container type"
    :items="filters"
    color="primary"
    density="compact"
    variant="outlined"
    hide-details
    @update:model-value="onSelectUpdate"
  >
    <template #prepend-inner>
      <v-icon
        icon="mdi-filter-variant"
        :color="selectedFilterParam ? 'primary' : 'medium-emphasis'"
        style="opacity: 1"
      />
    </template>
  </Select>
</template>

<style scoped lang="scss">
.v-select {
  :deep(.v-select__selection) {
    color: rgb(var(--v-theme-primary));
  }
}
</style>
