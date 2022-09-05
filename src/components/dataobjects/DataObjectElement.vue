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
import { handleError } from "@/utils/error-handling";
import type { DataObject, ResponseError } from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface RelatedObjectsTableData {
  dataObject?: DataObject;
}

export default defineComponent({
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
    this.retrieveDataObject();
  },
  methods: {
    retrieveDataObject() {
      DataObjectService.getDataObject({
        collectionId: this.collectionId,
        dataObjectId: this.dataObjectId,
      })
        .then(response => {
          this.dataObject = response;
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching data object");
        });
    },
  },
});
</script>
