<script setup lang="ts">
import type {
  DataObject,
  DataObjectReference,
} from "@dlr-shepard/shepard-client";
import type { PropType } from "vue";

defineProps({
  dataObjectReference: {
    type: Object as PropType<DataObjectReference>,
    required: true,
  },
  referencedDataObject: {
    type: Object as PropType<DataObject>,
    required: true,
  },
});
</script>

<template>
  <div>
    <small>
      {{ dataObjectReference.relationship }}:
      <span v-if="dataObjectReference.id">
        <b-link
          :to="{
            name: 'DataObject',
            params: {
              collectionId: referencedDataObject.collectionId,
              dataObjectId: referencedDataObject.id,
            },
          }"
        >
          <b>{{ referencedDataObject.name }}</b> | ID:
          {{ referencedDataObject.id }}
        </b-link>
      </span>
      <span v-else class="text-danger">DateObject Deleted</span>
    </small>
  </div>
</template>
