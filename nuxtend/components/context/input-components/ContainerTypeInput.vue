<script setup lang="ts">
import { ContainerType } from "@dlr-shepard/backend-client";

const containerTypeList = new Map<string, ContainerType>();
containerTypeList.set("File", ContainerType.File);
containerTypeList.set("Timeseries", ContainerType.Timeseries);
containerTypeList.set("Structured Data", ContainerType.Structureddata);
containerTypeList.set("Spatial Data", ContainerType.Spatialdata);

const currentSelected = ref<string>("File");

const containerType = defineModel<ContainerType>("containerType", {
  required: true,
});

function updateContainerType() {
  const selectedContainerType = containerTypeList.get(currentSelected.value);
  if (!selectedContainerType) {
    return;
  }
  containerType.value = selectedContainerType;
}
</script>

<template>
  <v-select
    v-model:model-value="currentSelected"
    :items="Array.from(containerTypeList.keys())"
    label="Container*"
    variant="outlined"
    density="compact"
    color="primary"
    require
    hide-details
    @update:model-value="updateContainerType"
  >
    <template #item="{ props: listItemProps, item }">
      <v-list-item
        v-bind="listItemProps"
        :title="item.value === ContainerType.File ? 'File' : item.value"
      />
    </template>
    <template #selection="{ item }">
      {{ item.value === ContainerType.File ? "File" : item.value }}
    </template>
  </v-select>
</template>
