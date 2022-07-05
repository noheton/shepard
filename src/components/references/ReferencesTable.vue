<template>
  <b-card no-body>
    <b-tabs card lazy>
      <b-tab title="Timeseries">
        <TimeseriesReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>

      <b-tab title="Strucured Data">
        <StructuredDataReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>

      <b-tab title="File">
        <FileReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>

      <b-tab title="URI">
        <UriReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>

      <b-tab title="Collection">
        <CollectionReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>

      <b-tab title="Data Object">
        <DataObjectReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
        />
      </b-tab>
    </b-tabs>
  </b-card>
</template>

<script lang="ts">
import CollectionReferencesList from "@/components/references/CollectionReferencesList.vue";
import DataObjectReferencesList from "@/components/references/DataObjectReferencesList.vue";
import FileReferencesList from "@/components/references/FileReferencesList.vue";
import StructuredDataReferencesList from "@/components/references/StructuredDataReferencesList.vue";
import TimeseriesReferencesList from "@/components/references/TimeseriesReferencesList.vue";
import UriReferencesList from "@/components/references/UriReferencesList.vue";
import { DataObject } from "@dlr-shepard/shepard-client";
import { defineComponent, PropType } from "vue";

export default defineComponent({
  components: {
    TimeseriesReferencesList,
    StructuredDataReferencesList,
    FileReferencesList,
    UriReferencesList,
    CollectionReferencesList,
    DataObjectReferencesList,
  },
  props: {
    currentDataObject: {
      type: Object as PropType<DataObject>,
      required: true,
    },
  },
});
</script>
