<script lang="ts" setup>
import type { TimeseriesEntity } from "@dlr-shepard/backend-client";

defineProps<{ measurements: TimeseriesEntity[] }>();

const headers = ref([
  { title: "Measurement", key: "measurement", sortable: true },
  { title: "Device", key: "device", sortable: true },
  { title: "Location", key: "location", sortable: true },
  { title: "Symbolic Name", key: "symbolicName", sortable: true },
  { title: "Field", key: "field", sortable: true },
]);
</script>

<template>
  <DataTable
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items="measurements"
    hover
    item-value="measurement"
  >
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination :total-visible="6" />
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }
}
</style>
