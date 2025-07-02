<script setup lang="ts">
import { QueryType } from "@dlr-shepard/backend-client";
import Select from "~/components/common/Select.vue";

defineProps<{
  noRequiredHint?: boolean;
}>();

const queryType = defineModel<QueryType>("queryType", {
  required: true,
});

const queryTypeUnifiedSearch = [
  { value: "Collection", text: "Collection" },
  { value: "DataObject", text: "Data Object" },
  { value: "Reference", text: "Reference" },
  { value: "StructuredData", text: "StructuredData" },
];
/* // nataku
const queryTypeContainerSearch = [
  { value: "FILE", text: "File Container" },
  { value: "STRUCTUREDDATA", text: "Structured Data Container" },
  { value: "TIMESERIES", text: "Timeseries Container" },
];
*/
</script>

<template>
  <v-row>
    <v-col cols="4">
      <Select
        v-model:model-value="queryType"
        :items="queryTypeUnifiedSearch ?? Object.values(queryType)"
        :label="`Query Type${noRequiredHint ? '' : '*'}`"
        variant="outlined"
        density="compact"
        color="primary"
        require
        hide-details
      >
        <template #item="{ props: listItemProps, item }">
          <v-list-item
            v-bind="listItemProps"
            :title="
              item.value === QueryType.Collection ? 'Collection' : item.value
            "
          />
        </template>
        <template #selection="{ item }">
          {{ item.value === QueryType.Collection ? "Collection" : item.value }}
        </template>
      </Select>
    </v-col>
  </v-row>
</template>
