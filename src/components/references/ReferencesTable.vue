<script setup lang="ts">
import CollectionReferencesList from "@/components/references/CollectionReferencesList.vue";
import DataObjectReferencesList from "@/components/references/DataObjectReferencesList.vue";
import FileReferencesList from "@/components/references/FileReferencesList.vue";
import StructuredDataReferencesList from "@/components/references/StructuredDataReferencesList.vue";
import TimeseriesReferencesList from "@/components/references/TimeseriesReferencesList.vue";
import UriReferencesList from "@/components/references/UriReferencesList.vue";
import ReferenceService from "@/services/referenceService";
import { handleError } from "@/utils/error-handling";
import type { DataObject, ResponseError } from "@dlr-shepard/shepard-client";
import { onMounted, ref, type PropType } from "vue";

const props = defineProps({
  currentDataObject: {
    type: Object as PropType<DataObject>,
    required: true,
  },
});

const countReferences = ref({
  timeseriesReferences: 0,
  structurdDataReferences: 0,
  fileReferences: 0,
  uriReferences: 0,
  collectionReferences: 0,
  dataObjectReferences: 0,
  lostReferences: 0,
});

function retrieveReferences() {
  if (!props.currentDataObject.collectionId || !props.currentDataObject.id) {
    return;
  }
  ReferenceService.getAllReferences({
    collectionId: +props.currentDataObject.collectionId,
    dataObjectId: +props.currentDataObject.id,
  })
    .then(response => {
      response.forEach(reference => {
        switch (reference.type) {
          case "TimeseriesReference":
            countReferences.value.timeseriesReferences++;
            break;
          case "StructuredDataReference":
            countReferences.value.structurdDataReferences++;
            break;
          case "FileReference":
            countReferences.value.fileReferences++;
            break;
          case "URIReference":
            countReferences.value.uriReferences++;
            break;
          case "CollectionReference":
            countReferences.value.collectionReferences++;
            break;
          case "DataObjectReference":
            countReferences.value.dataObjectReferences++;
            break;
          default:
            countReferences.value.lostReferences++;
            break;
        }
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching all references");
    });
}

onMounted(() => {
  retrieveReferences();
});
</script>

<template>
  <b-card no-body>
    <b-tabs
      v-if="currentDataObject.collectionId && currentDataObject.id"
      card
      lazy
    >
      <b-tab>
        <template #title>
          Timeseries ({{ countReferences?.timeseriesReferences }})
        </template>
        <TimeseriesReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="
            countReferences.timeseriesReferences = $event
          "
        />
      </b-tab>

      <b-tab>
        <template #title>
          Structured Data ({{ countReferences?.structurdDataReferences }})
        </template>
        <StructuredDataReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="
            countReferences.structurdDataReferences = $event
          "
        />
      </b-tab>

      <b-tab>
        <template #title>
          File ({{ countReferences?.fileReferences }})
        </template>
        <FileReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="countReferences.fileReferences = $event"
        />
      </b-tab>

      <b-tab>
        <template #title> URI ({{ countReferences?.uriReferences }}) </template>
        <UriReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="countReferences.uriReferences = $event"
        />
      </b-tab>

      <b-tab>
        <template #title>
          Collection ({{ countReferences?.collectionReferences }})
        </template>
        <CollectionReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="
            countReferences.collectionReferences = $event
          "
        />
      </b-tab>

      <b-tab>
        <template #title>
          Data Object ({{ countReferences?.dataObjectReferences }})
        </template>
        <DataObjectReferencesList
          :current-collection-id="currentDataObject.collectionId"
          :current-data-object-id="currentDataObject.id"
          @reference-count-changed="
            countReferences.dataObjectReferences = $event
          "
        />
      </b-tab>
    </b-tabs>
  </b-card>
</template>
