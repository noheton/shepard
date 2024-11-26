<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectDescriptionProps {
  dataObject: DataObject;
}
defineProps<DataObjectDescriptionProps>();
</script>

<template>
  <v-container fluid>
    <v-row>{{ dataObject.description }}</v-row>
  </v-container>
</template>
