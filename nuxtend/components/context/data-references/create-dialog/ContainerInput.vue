<script setup lang="ts">
import type { ContainerType } from "@dlr-shepard/backend-client";

defineProps<{ collectionId: number }>();

const containerId = defineModel<number | null>("containerId", {
  required: true,
});
const emit = defineEmits<{
  (e: "searchEnded", id: number, type: ContainerType): void;
}>();

function searchEnded(id: number | null, type: ContainerType | null) {
  if (!id || !type) return; // gibt es einen Container der die ID 0 haben kann? Der könnte jetzt nicht ausgewählt werden!
  containerId.value = id;
  emit("searchEnded", id, type);
}
</script>

<template>
  <v-row>
    <v-col cols="12">
      <ContainerAutocomplete
        input-label="Container Name or ID..."
        :initial-container-id="containerId"
        @search-ended="searchEnded"
      />
    </v-col>
  </v-row>
</template>
