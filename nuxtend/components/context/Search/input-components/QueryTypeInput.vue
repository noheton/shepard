<script lang="ts" setup>
import { ContainerType, QueryType } from "@dlr-shepard/backend-client";
import Select from "~/components/common/Select.vue";

defineProps<{
  noRequiredHint?: boolean;
}>();

export type QueryContainerType = QueryType | ContainerType;
const queryType = defineModel<QueryContainerType>("queryType", {
  required: true,
});

const queryTypeUnifiedSearch = [
  { value: QueryType.Collection, text: "Collection" },
  { value: QueryType.DataObject, text: "Data Object" },
  { value: QueryType.Reference, text: "File or Timeseries Reference" },
  { value: QueryType.StructuredData, text: "Structured Data Reference" },
  { value: ContainerType.File, text: "File Container" },
  { value: ContainerType.Structureddata, text: "Structured Data Container" },
  { value: ContainerType.Timeseries, text: "Timeseries Container" },
];
</script>

<template>
  <Select
    v-model:model-value="queryType"
    :items="queryTypeUnifiedSearch ?? Object.values(queryType)"
    :label="`Query Type${noRequiredHint ? '' : '*'}`"
    color="primary"
    density="compact"
    hide-details
    item-title="text"
    item-value="value"
    require
    variant="outlined"
  />
</template>
