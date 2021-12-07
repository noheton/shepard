<template>
  <b-card no-body>
    <b-tabs card>
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

<script lang="ts">
import DataObjectList from "@/components/dataobjects/DataObjectList.vue";
import { DataObject } from "@dlr-shepard/shepard-client";
import Vue, { PropType } from "vue";

interface RelatedObjectsTableData {
  childrenIds: number[];
  predecessorIds: number[];
  successorIds: number[];
}

export default Vue.extend({
  components: { DataObjectList },
  props: {
    currentDataObject: {
      type: Object as PropType<DataObject>,
      required: true,
    },
  },
  data() {
    return {
      childrenIds: this.currentDataObject.childrenIds || [],
      predecessorIds: this.currentDataObject.predecessorIds || [],
      successorIds: this.currentDataObject.successorIds || [],
    } as RelatedObjectsTableData;
  },
});
</script>
