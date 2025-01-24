<script setup lang="ts">
import {
  ContainerFilterTypes,
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";

const selectedFilter = defineModel<ContainerFilterType | null>();
const emit = defineEmits(["onSelectUpdate"]);
const filters = Object.values(ContainerFilterTypes).map(containerType => ({
  title: ContainerTypeName[containerType],
  value: containerType,
}));
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
    @update:model-value="emit('onSelectUpdate')"
  />
</template>

<style scoped lang="scss">
.v-select {
  :deep(.v-select__selection) {
    color: rgb(var(--v-theme-primary));
  }
}
</style>
