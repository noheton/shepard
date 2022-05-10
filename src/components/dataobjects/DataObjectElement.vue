<template>
  <b-card no-body>
    <b-list-group>
      <DataObjectListItem v-if="dataObject" :data-object="dataObject" />
    </b-list-group>
  </b-card>
</template>

<script lang="ts">
import DataObjectListItem from "@/components/dataobjects/DataObjectListItem.vue";
import DataObjectService from "@/services/dataObjectService";
import { DataObject } from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface RelatedObjectsTableData {
  dataObject?: DataObject;
}

export default Vue.extend({
  components: { DataObjectListItem },
  props: {
    collectionId: {
      type: Number,
      required: true,
    },
    dataObjectId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      dataObject: undefined,
    } as RelatedObjectsTableData;
  },
  mounted() {
    this.retrieveDataObjects();
  },
  methods: {
    retrieveDataObjects() {
      DataObjectService.getDataObject({
        collectionId: this.collectionId,
        dataObjectId: this.dataObjectId,
      })
        .then(response => {
          this.dataObject = response;
        })
        .catch(e => {
          const error = "Error while fetching Data Object: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
