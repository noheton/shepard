<script setup lang="ts">
import type {
  Collection,
  CollectionReference,
} from "@dlr-shepard/backend-client";
import type { PropType } from "vue";

defineProps({
  collectionReference: {
    type: Object as PropType<CollectionReference>,
    required: true,
  },
  referencedCollection: {
    type: Object as PropType<Collection | undefined>,
    required: true,
  },
});
</script>

<template>
  <div>
    <small>
      {{ collectionReference.relationship }}:

      <span v-if="collectionReference.id">
        <b-link
          :to="{
            name: 'Collection',
            params: {
              collectionId: referencedCollection?.id,
            },
          }"
        >
          <b>{{ referencedCollection?.name }}</b> | ID:
          {{ referencedCollection?.id }}
        </b-link>
      </span>
      <span v-else class="text-danger">Collection Deleted</span>
    </small>
  </div>
</template>
