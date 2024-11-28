<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectDescriptionProps {
  dataObject: DataObject;
}
defineProps<DataObjectDescriptionProps>();
</script>

<template>
  <v-container fluid class="pa-0">
    <v-row no-gutters>{{ dataObject.description }}</v-row>
  </v-container>
</template>
