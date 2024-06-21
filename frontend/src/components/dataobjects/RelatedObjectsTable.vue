<script setup lang="ts">
import DataObjectList from "@/components/dataobjects/DataObjectList.vue";
import type { DataObject } from "@dlr-shepard/shepard-client";
import { ref, type PropType } from "vue";

const props = defineProps({
  currentDataObject: {
    type: Object as PropType<DataObject>,
    required: true,
  },
});

const childrenIds = ref<number[]>(props.currentDataObject.childrenIds || []);
const predecessorIds = ref<number[]>(
  props.currentDataObject.predecessorIds || [],
);
const successorIds = ref<number[]>(props.currentDataObject.successorIds || []);
</script>

<template>
  <b-card no-body>
    <b-tabs v-if="currentDataObject.collectionId" card>
      <b-tab title="Children" :disabled="!childrenIds.length">
        <DataObjectList
          :current-collection-id="currentDataObject.collectionId"
          :parent-id="currentDataObject.id"
          :max-objects="childrenIds.length"
        />
      </b-tab>
      <b-tab title="Predecessors" :disabled="!predecessorIds.length">
        <DataObjectList
          :current-collection-id="currentDataObject.collectionId"
          :successor-id="currentDataObject.id"
          :max-objects="predecessorIds.length"
        />
      </b-tab>
      <b-tab title="Successors" :disabled="!successorIds.length">
        <DataObjectList
          :current-collection-id="currentDataObject.collectionId"
          :predecessor-id="currentDataObject.id"
          :max-objects="successorIds.length"
        />
      </b-tab>
    </b-tabs>
  </b-card>
</template>
